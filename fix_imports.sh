# Repositories
sed -i '' -e '/import org.springframework.stereotype.Repository;/a\
import com.enterprise.inventory.entity.UserEntity;
' src/main/java/com/enterprise/inventory/repository/UserRepository.java

sed -i '' -e '/import org.springframework.stereotype.Repository;/a\
import com.enterprise.inventory.entity.TokenBlacklistJpaEntity;
' src/main/java/com/enterprise/inventory/repository/TokenBlacklistRepository.java

sed -i '' -e '/import org.springframework.stereotype.Repository;/a\
import com.enterprise.inventory.entity.InventoryJpaEntity;
' src/main/java/com/enterprise/inventory/repository/InventoryRepository.java

sed -i '' -e '/import org.springframework.stereotype.Repository;/a\
import com.enterprise.inventory.entity.StockMovementJpaEntity;
' src/main/java/com/enterprise/inventory/repository/StockMovementRepository.java

sed -i '' -e '/import org.springframework.stereotype.Repository;/a\
import com.enterprise.inventory.entity.ReservationLockJpaEntity;\
import com.enterprise.inventory.entity.ReservationLockJpaEntity.ReservationLockId;
' src/main/java/com/enterprise/inventory/repository/ReservationLockRepository.java

sed -i '' -e '/import org.springframework.stereotype.Repository;/a\
import com.enterprise.inventory.entity.IdempotencyKeyJpaEntity;
' src/main/java/com/enterprise/inventory/repository/IdempotencyKeyRepository.java

sed -i '' -e '/import org.springframework.security.web.SecurityFilterChain;/a\
import com.enterprise.inventory.security.JwtAuthenticationFilter;\
import com.enterprise.inventory.filter.CorrelationIdFilter;\
import com.enterprise.inventory.filter.RateLimitingFilter;
' src/main/java/com/enterprise/inventory/config/SecurityConfig.java

