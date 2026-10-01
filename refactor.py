import os
import shutil
import re

base_pkg = "com.enterprise.inventory"
src_base = "src/main/java/com/enterprise/inventory"
test_base = "src/test/java/com/enterprise/inventory"

file_mappings = {
    "inventory/web/controller/AuthController.java": "controller",
    "inventory/web/controller/InventoryController.java": "controller",
    "inventory/web/controller/UserController.java": "controller",
    "inventory/web/controller/GlobalExceptionHandler.java": "exception",
    
    "inventory/web/dto": "dto",
    
    "inventory/web/ApiPaths.java": "", # root package
    
    "inventory/application/CacheHydrationService.java": "service",
    "inventory/application/IdempotencyService.java": "service",
    "inventory/application/InventoryService.java": "service",
    "inventory/application/UserService.java": "service",
    "inventory/application/InsufficientStockException.java": "exception",
    "inventory/application/InventoryEvent.java": "messaging",
    
    "inventory/infrastructure/config/CorrelationIdFilter.java": "filter",
    "inventory/infrastructure/config/RateLimitingFilter.java": "filter",
    "inventory/infrastructure/config/DataInitializer.java": "config",
    "inventory/infrastructure/config/SecurityConfig.java": "config",
    "inventory/infrastructure/config/DatabaseUserDetailsService.java": "security",
    "inventory/infrastructure/config/JwtAuthenticationFilter.java": "security",
    "inventory/infrastructure/config/JwtUtil.java": "security",
    "inventory/infrastructure/config/TokenBlacklistService.java": "security",
    "inventory/infrastructure/config/SapSyncRecoveryJob.java": "scheduling",
    
    "inventory/infrastructure/messaging/InventoryEventPublisher.java": "messaging",
    
    "inventory/infrastructure/persistence/IdempotencyKeyJpaEntity.java": "entity",
    "inventory/infrastructure/persistence/InventoryJpaEntity.java": "entity",
    "inventory/infrastructure/persistence/ReservationLockJpaEntity.java": "entity",
    "inventory/infrastructure/persistence/StockMovementJpaEntity.java": "entity",
    "inventory/infrastructure/persistence/TokenBlacklistJpaEntity.java": "entity",
    "inventory/infrastructure/persistence/UserEntity.java": "entity",
    "inventory/infrastructure/persistence/Role.java": "entity",
    "inventory/infrastructure/persistence/Permission.java": "entity",
    
    "inventory/infrastructure/persistence/IdempotencyKeyRepository.java": "repository",
    "inventory/infrastructure/persistence/InventoryRepository.java": "repository",
    "inventory/infrastructure/persistence/ReservationLockRepository.java": "repository",
    "inventory/infrastructure/persistence/StockMovementRepository.java": "repository",
    "inventory/infrastructure/persistence/TokenBlacklistRepository.java": "repository",
    "inventory/infrastructure/persistence/UserRepository.java": "repository",
}

def get_new_package_and_path(old_relative_path):
    if old_relative_path in file_mappings:
        sub_pkg = file_mappings[old_relative_path]
    elif old_relative_path.startswith("inventory/web/dto/"):
        sub_pkg = "dto"
    elif old_relative_path.startswith("inventory/infrastructure/persistence/"):
        name = os.path.basename(old_relative_path)
        if "Repository" in name:
            sub_pkg = "repository"
        else:
            sub_pkg = "entity"
    elif old_relative_path.startswith("inventory/application/"):
        name = os.path.basename(old_relative_path)
        if "Exception" in name:
            sub_pkg = "exception"
        elif "Event" in name:
            sub_pkg = "messaging"
        else:
            sub_pkg = "service"
    elif old_relative_path.startswith("inventory/web/controller/"):
        name = os.path.basename(old_relative_path)
        if "Exception" in name:
            sub_pkg = "exception"
        else:
            sub_pkg = "controller"
    elif old_relative_path.startswith("inventory/infrastructure/config/"):
        name = os.path.basename(old_relative_path)
        if "Filter" in name:
            sub_pkg = "filter"
        elif "Security" in name or "Config" in name or "Initializer" in name:
            sub_pkg = "config"
        elif "Jwt" in name or "Token" in name or "Auth" in name or "UserDetails" in name:
            sub_pkg = "security"
        elif "Job" in name:
            sub_pkg = "scheduling"
        else:
            sub_pkg = "config"
    else:
        return None, None

    pkg = base_pkg if not sub_pkg else f"{base_pkg}.{sub_pkg}"
    path = sub_pkg + "/" if sub_pkg else ""
    return pkg, path

import_replacements = {}
file_moves = []

def scan_dir(base_dir):
    if not os.path.exists(base_dir):
        return
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".java"):
                full_path = os.path.join(root, f)
                rel_path = os.path.relpath(full_path, base_dir)
                if not rel_path.startswith("inventory/"):
                    continue
                new_pkg, new_rel_dir = get_new_package_and_path(rel_path)
                if new_pkg is not None:
                    class_name = f[:-5]
                    old_fqn = f"com.enterprise.inventory.{rel_path[:-5].replace('/', '.')}"
                    new_fqn = f"{new_pkg}.{class_name}"
                    import_replacements[old_fqn] = new_fqn
                    new_full_path = os.path.join(base_dir, new_rel_dir, f)
                    file_moves.append((full_path, new_full_path, new_pkg))

scan_dir(src_base)
scan_dir(test_base)

for k, v in list(import_replacements.items()):
    if not k.endswith("Test"):
        import_replacements[k + "Test"] = v + "Test"

def replace_in_file(path, new_pkg=None):
    with open(path, 'r') as f:
        content = f.read()
    
    if new_pkg:
        content = re.sub(r'^package\s+[\w\.]+;', f'package {new_pkg};', content, flags=re.MULTILINE)
    
    for old_fqn, new_fqn in import_replacements.items():
        if old_fqn != new_fqn:
            content = re.sub(rf'\b{re.escape(old_fqn)}\b', new_fqn, content)
            
            old_pkg = ".".join(old_fqn.split(".")[:-1])
            new_pkg_for_wild = ".".join(new_fqn.split(".")[:-1])
            if old_pkg != new_pkg_for_wild:
                content = content.replace(f"import {old_pkg}.*;", f"import {new_pkg_for_wild}.*;")

    
    content = content.replace('import com.enterprise.inventory.inventory.web.ApiPaths;', 'import com.enterprise.inventory.ApiPaths;')
    content = content.replace('com.enterprise.inventory.inventory.web.ApiPaths', 'com.enterprise.inventory.ApiPaths')

    with open(path, 'w') as f:
        f.write(content)

for old_path, new_path, new_pkg in file_moves:
    os.makedirs(os.path.dirname(new_path), exist_ok=True)
    shutil.move(old_path, new_path)
    replace_in_file(new_path, new_pkg)

def update_imports_only(base_dir):
    if not os.path.exists(base_dir): return
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".java"):
                full_path = os.path.join(root, f)
                replace_in_file(full_path, None)

update_imports_only(src_base)
update_imports_only(test_base)

print("Refactoring complete.")
