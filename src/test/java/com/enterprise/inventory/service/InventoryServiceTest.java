package com.enterprise.inventory.service;

import com.enterprise.inventory.entity.InventoryJpaEntity;
import com.enterprise.inventory.repository.InventoryRepository;
import com.enterprise.inventory.entity.StockMovementJpaEntity;
import com.enterprise.inventory.repository.StockMovementRepository;
import com.enterprise.inventory.dto.InventoryResponseDTO;
import com.enterprise.inventory.dto.PickReserveDTO;
import com.enterprise.inventory.dto.PickConfirmDTO;
import com.enterprise.inventory.dto.ReceiveStockDTO;
import com.enterprise.inventory.dto.BatchPutawayDTO;
import com.enterprise.inventory.dto.ContainerPutawayDTO;
import com.enterprise.inventory.dto.ItemPutawayDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.enterprise.inventory.messaging.InventoryEvent;
import com.enterprise.inventory.exception.InsufficientStockException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private StockMovementRepository movementRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private com.enterprise.inventory.repository.ReservationLockRepository reservationLockRepository;

    @Mock
    private com.enterprise.inventory.config.AppProperties appProperties;

    @InjectMocks
    private InventoryService inventoryService;

    private final UUID locationId = UUID.randomUUID();
    private final UUID containerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String sku = "SKU-123";

    @BeforeEach
    void setUp() {
        lenient().when(idempotencyService.getOrCompute(anyString(), any(), eq(InventoryResponseDTO.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        
        lenient().when(idempotencyService.getOrCompute(anyString(), any(), any(TypeReference.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
                
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.hasKey("wms:cache:valid")).thenReturn(true);
        
        com.enterprise.inventory.config.AppProperties.Security security = new com.enterprise.inventory.config.AppProperties.Security();
        security.getLocks().setReservationTtl(java.time.Duration.ofHours(2));
        lenient().when(appProperties.getSecurity()).thenReturn(security);
    }

    @Test
    void testReceiveStock_NewRecord() {
        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.empty());

        StockMovementJpaEntity mockMovement = mock(StockMovementJpaEntity.class);
        when(mockMovement.getId()).thenReturn(UUID.randomUUID());
        when(movementRepository.save(any())).thenReturn(mockMovement);

        ReceiveStockDTO request = new ReceiveStockDTO(sku, locationId, containerId, 50, "TASK-1");
        InventoryResponseDTO response = inventoryService.receiveStock(
                request, userId, "key-1");

        assertEquals(sku, response.sku());
        assertEquals(50, response.qtyOnHand());
        assertEquals(50, response.qtyAvailable());
        assertEquals(0, response.qtyReserved());

        verify(inventoryRepository).save(any(InventoryJpaEntity.class));
        verify(movementRepository).save(any(StockMovementJpaEntity.class));
        verify(applicationEventPublisher).publishEvent(any(InventoryEvent.class));
    }

    @Test
    void testReserveStock_Success() {
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(0);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));

        PickReserveDTO request = new PickReserveDTO(sku, locationId, containerId, 20, "TASK-2");
        InventoryResponseDTO response = inventoryService.reserveStock(request, userId, "key-2");

        assertEquals(100, response.qtyOnHand());
        assertEquals(20, response.qtyReserved());
        assertEquals(80, response.qtyAvailable()); // 100 on hand - 20 reserved
        
        verify(inventoryRepository).save(existingEntity);
    }

    @Test
    void testReserveStock_ThrowsWhenMissing() {
        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.empty());

        PickReserveDTO request = new PickReserveDTO(sku, locationId, containerId, 20, "TASK-3");

        assertThrows(InsufficientStockException.class, () -> {
            inventoryService.reserveStock(request, userId, "key-3");
        });
    }

    @Test
    void testConfirmPick_ShortPick() {
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(20);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));
                
        when(valueOperations.get(anyString())).thenReturn("20");

        StockMovementJpaEntity mockMovement = mock(StockMovementJpaEntity.class);
        when(mockMovement.getId()).thenReturn(UUID.randomUUID());
        when(movementRepository.save(any())).thenReturn(mockMovement);

        PickConfirmDTO request = new PickConfirmDTO(sku, locationId, containerId, 20, 15, "TASK-4");
        InventoryResponseDTO response = inventoryService.confirmPick(request, userId, "key-4");

        assertEquals(85, response.qtyOnHand()); // 100 - 15 picked
        assertEquals(0, response.qtyReserved()); // all 20 reservations released
        assertEquals(85, response.qtyAvailable());

        ArgumentCaptor<InventoryEvent> eventCaptor = ArgumentCaptor.forClass(InventoryEvent.class);
        verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());
        
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(e -> e.eventType() == InventoryEvent.Type.PICK_CONFIRMED));
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(e -> e.eventType() == InventoryEvent.Type.SHORT_PICK && e.discrepancy() == 5));
    }

    @Test
    void testConfirmPick_ThrowsWhenActualExceedsReserved() {
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(20);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));
                
        when(valueOperations.get(anyString())).thenReturn("20");

        PickConfirmDTO request = new PickConfirmDTO(sku, locationId, containerId, 20, 25, "TASK-5");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.confirmPick(request, userId, "key-5");
        });
        assertEquals("Actual amount cannot exceed reserved amount", ex.getMessage());
    }

    @Test
    void testConfirmPick_ThrowsWhenActualNegative() {
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(20);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));
                
        when(valueOperations.get(anyString())).thenReturn("20");

        PickConfirmDTO request = new PickConfirmDTO(sku, locationId, containerId, 20, -5, "TASK-6");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.confirmPick(request, userId, "key-6");
        });
        assertEquals("Actual amount cannot be negative", ex.getMessage());
    }

    @Test
    void testConfirmPick_OptimisticLockingFailure() {
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(20);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));
                
        when(valueOperations.get(anyString())).thenReturn("20");

        when(inventoryRepository.save(any(InventoryJpaEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(InventoryJpaEntity.class, existingEntity.getId()));

        PickConfirmDTO request = new PickConfirmDTO(sku, locationId, containerId, 20, 20, "TASK-7");

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            inventoryService.confirmPick(request, userId, "key-7");
        });
    }

    @Test
    void testReceiveStockBatch_PartialFailure() {
        String sku1 = "SKU-A";
        String sku2 = "SKU-B"; // this one will fail
        UUID dest1 = UUID.randomUUID();
        UUID dest2 = UUID.randomUUID();

        ItemPutawayDTO item1 = new ItemPutawayDTO(sku1, dest1, 10);
        ItemPutawayDTO item2 = new ItemPutawayDTO(sku2, dest2, 0); // Invalid amount <= 0
        
        ContainerPutawayDTO container = new ContainerPutawayDTO(containerId, List.of(item1, item2));
        BatchPutawayDTO request = new BatchPutawayDTO("BATCH-1", locationId, List.of(container));

        when(inventoryRepository.findExactMatch(sku1, dest1, containerId)).thenReturn(Optional.empty());
        when(inventoryRepository.findExactMatch(sku2, dest2, containerId)).thenReturn(Optional.empty());

        StockMovementJpaEntity mockMovement = mock(StockMovementJpaEntity.class);
        when(mockMovement.getId()).thenReturn(UUID.randomUUID());
        lenient().when(movementRepository.save(any())).thenReturn(mockMovement);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.receiveStockBatch(request, userId, "key-batch-1");
        });
        
        assertEquals("Receive amount must be positive", ex.getMessage());
    }
}
