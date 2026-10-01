package com.enterprise.inventory.scheduling;

import com.enterprise.inventory.messaging.InventoryEventPublisher;
import com.enterprise.inventory.entity.StockMovementJpaEntity;
import com.enterprise.inventory.repository.StockMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SapSyncRecoveryJobTest {

    @Mock
    private StockMovementRepository movementRepository;

    @Mock
    private InventoryEventPublisher eventPublisher;

    @InjectMocks
    private SapSyncRecoveryJob recoveryJob;

    @Test
    void retryFailedSapSync_RepublishesAndMarksAsSynced() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID locId = UUID.randomUUID();
        
        StockMovementJpaEntity m1 = StockMovementJpaEntity.of(
                StockMovementJpaEntity.MovementType.RECEIVE,
                "SKU-1", null, locId, null, 10, "TASK-1", "PUTAWAY_TASK", UUID.randomUUID()
        );
        StockMovementJpaEntity mock1 = spy(m1);
        when(mock1.getId()).thenReturn(id1);

        when(movementRepository.findUnsyncedMovementsOlderThan(any(Instant.class), anyInt()))
                .thenReturn(List.of(mock1));

        recoveryJob.retryFailedSapSync();

        verify(eventPublisher).publishStockReceivedSync("SKU-1", locId, null, 10, "TASK-1");
        verify(movementRepository).markSyncedToSap(id1);
    }

    @Test
    void retryFailedSapSync_IgnoresWhenNoPendingMovements() throws Exception {
        when(movementRepository.findUnsyncedMovementsOlderThan(any(Instant.class), anyInt()))
                .thenReturn(List.of());

        recoveryJob.retryFailedSapSync();

        verifyNoInteractions(eventPublisher);
        verify(movementRepository, never()).markSyncedToSap(any());
    }

    @Test
    void retryFailedSapSync_ContinuesOnFailure() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        StockMovementJpaEntity m1 = StockMovementJpaEntity.of(
                StockMovementJpaEntity.MovementType.RECEIVE, "SKU-1", null, null, null, 10, "TASK-1", "T", UUID.randomUUID()
        );
        StockMovementJpaEntity mock1 = spy(m1);
        when(mock1.getId()).thenReturn(id1);

        StockMovementJpaEntity m2 = StockMovementJpaEntity.of(
                StockMovementJpaEntity.MovementType.PICK, "SKU-2", null, null, null, 5, "TASK-2", "T", UUID.randomUUID()
        );
        StockMovementJpaEntity mock2 = spy(m2);
        when(mock2.getId()).thenReturn(id2);

        when(movementRepository.findUnsyncedMovementsOlderThan(any(Instant.class), anyInt()))
                .thenReturn(List.of(mock1, mock2));

        doThrow(new RuntimeException("Kafka down")).when(eventPublisher).publishStockReceivedSync(any(), any(), any(), anyInt(), any());

        recoveryJob.retryFailedSapSync();

        verify(eventPublisher).publishStockReceivedSync(any(), any(), any(), anyInt(), any());
        verify(movementRepository, never()).markSyncedToSap(id1); // Should not mark synced

        verify(eventPublisher).publishPickConfirmedSync(any(), any(), any(), anyInt(), any());
        verify(movementRepository).markSyncedToSap(id2); // Second one succeeds
    }
}
