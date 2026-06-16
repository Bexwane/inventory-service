package com.enterprise.inventory.inventory.application;

import com.enterprise.inventory.inventory.infrastructure.persistence.InventoryJpaEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.InventoryRepository;
import com.enterprise.inventory.inventory.infrastructure.persistence.StockMovementJpaEntity;
import com.enterprise.inventory.inventory.infrastructure.persistence.StockMovementRepository;
import com.enterprise.inventory.inventory.web.dto.InventoryResponseDTO;
import com.enterprise.inventory.inventory.web.dto.PickReserveDTO;
import com.enterprise.inventory.inventory.web.dto.PickConfirmDTO;
import com.enterprise.inventory.inventory.web.dto.BatchPutawayDTO;
import com.enterprise.inventory.inventory.web.dto.ContainerPutawayDTO;
import com.enterprise.inventory.inventory.web.dto.ItemPutawayDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    @InjectMocks
    private InventoryService inventoryService;

    private final UUID locationId = UUID.randomUUID();
    private final UUID containerId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final String sku = "SKU-123";

    @BeforeEach
    void setUp() {
        // Mock IdempotencyService to just execute the lambda immediately without Redis
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
    }

    @Test
    void testReceiveStock_NewRecord() {
        // Given
        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.empty());

        StockMovementJpaEntity mockMovement = mock(StockMovementJpaEntity.class);
        when(mockMovement.getId()).thenReturn(UUID.randomUUID());
        when(movementRepository.save(any())).thenReturn(mockMovement);

        // When
        InventoryResponseDTO response = inventoryService.receiveStock(
                sku, locationId, containerId, 50, "TASK-1", userId, "key-1");

        // Then
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
        // Given
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(0);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));

        // When
        PickReserveDTO request = new PickReserveDTO(sku, locationId, containerId, 20, "TASK-2");
        InventoryResponseDTO response = inventoryService.reserveStock(request, userId, "key-2");

        // Then
        assertEquals(100, response.qtyOnHand());
        assertEquals(20, response.qtyReserved());
        assertEquals(80, response.qtyAvailable()); // 100 on hand - 20 reserved
        
        verify(inventoryRepository).save(existingEntity);
    }

    @Test
    void testReserveStock_ThrowsWhenMissing() {
        // Given
        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.empty());

        PickReserveDTO request = new PickReserveDTO(sku, locationId, containerId, 20, "TASK-3");

        // When & Then
        assertThrows(InsufficientStockException.class, () -> {
            inventoryService.reserveStock(request, userId, "key-3");
        });
    }

    @Test
    void testConfirmPick_ShortPick() {
        // Given
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

        // When
        // Reserved 20, but worker only found 15 (Short Pick)
        PickConfirmDTO request = new PickConfirmDTO(sku, locationId, containerId, 20, 15, "TASK-4");
        InventoryResponseDTO response = inventoryService.confirmPick(request, userId, "key-4");

        // Then
        assertEquals(85, response.qtyOnHand()); // 100 - 15 picked
        assertEquals(0, response.qtyReserved()); // all 20 reservations released
        assertEquals(85, response.qtyAvailable());

        // Verify two events were published: PICK_CONFIRMED and SHORT_PICK
        ArgumentCaptor<InventoryEvent> eventCaptor = ArgumentCaptor.forClass(InventoryEvent.class);
        verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());
        
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(e -> e.eventType() == InventoryEvent.Type.PICK_CONFIRMED));
        assertTrue(eventCaptor.getAllValues().stream()
                .anyMatch(e -> e.eventType() == InventoryEvent.Type.SHORT_PICK && e.discrepancy() == 5));
    }

    @Test
    void testConfirmPick_ThrowsWhenActualExceedsReserved() {
        // Given
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(20);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));
                
        when(valueOperations.get(anyString())).thenReturn("20");

        // When
        PickConfirmDTO request = new PickConfirmDTO(sku, locationId, containerId, 20, 25, "TASK-5");

        // Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.confirmPick(request, userId, "key-5");
        });
        assertEquals("Actual amount cannot exceed reserved amount", ex.getMessage());
    }

    @Test
    void testConfirmPick_ThrowsWhenActualNegative() {
        // Given
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(20);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));
                
        when(valueOperations.get(anyString())).thenReturn("20");

        // When
        PickConfirmDTO request = new PickConfirmDTO(sku, locationId, containerId, 20, -5, "TASK-6");

        // Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.confirmPick(request, userId, "key-6");
        });
        assertEquals("Actual amount cannot be negative", ex.getMessage());
    }

    @Test
    void testConfirmPick_OptimisticLockingFailure() {
        // Given
        InventoryJpaEntity existingEntity = new InventoryJpaEntity();
        existingEntity.setSku(sku);
        existingEntity.setQtyOnHand(100);
        existingEntity.setQtyReserved(20);

        when(inventoryRepository.findExactMatch(sku, locationId, containerId))
                .thenReturn(Optional.of(existingEntity));
                
        when(valueOperations.get(anyString())).thenReturn("20");

        // Simulate concurrent modification triggering a JPA OptimisticLockingFailure
        when(inventoryRepository.save(any(InventoryJpaEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(InventoryJpaEntity.class, existingEntity.getId()));

        PickConfirmDTO request = new PickConfirmDTO(sku, locationId, containerId, 20, 20, "TASK-7");

        // When & Then
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            inventoryService.confirmPick(request, userId, "key-7");
        });
    }

    @Test
    void testReceiveStockBatch_PartialFailure() {
        // Given a batch of 2 items
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

        // When & Then
        // Item 1 is processed, Item 2 throws IllegalArgumentException inside receiveStock(0)
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.receiveStockBatch(request, userId, "key-batch-1");
        });
        
        assertEquals("Receive amount must be positive", ex.getMessage());
        // In a real transactional environment, this exception causes the entire transaction to rollback, 
        // meaning Item 1 is not saved. Here we just assert the batch aborted midway.
    }
}
