package com.enterprise.inventory.service;

import com.enterprise.inventory.entity.Permission;
import com.enterprise.inventory.entity.Role;
import com.enterprise.inventory.entity.UserEntity;
import com.enterprise.inventory.repository.UserRepository;
import com.enterprise.inventory.dto.BatchPermissionDTO;
import com.enterprise.inventory.dto.CreateUserDTO;
import com.enterprise.inventory.dto.UpdateUserDTO;
import com.enterprise.inventory.dto.UserResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private UserEntity existingUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        existingUser = new UserEntity();
        existingUser.setId(userId);
        existingUser.setEmployeeId("EMP-001");
        existingUser.setUsername("testuser");
        existingUser.setRole(Role.WORKER);
        existingUser.setPermissions(Set.of(Permission.CAN_PICK));
        existingUser.setActive(true);
    }

    @Test
    void createUser_Success() {
        CreateUserDTO request = new CreateUserDTO(
                "EMP-002", "newuser", "password123", Role.MANAGER, Set.of(Permission.CAN_PICK, Permission.CAN_PUTAWAY));

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeId("EMP-002")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed_pw");

        UserResponseDTO response = userService.createUser(request);

        assertEquals("newuser", response.username());
        assertTrue(response.isActive());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void createUser_ThrowsWhenUsernameExists() {
        CreateUserDTO request = new CreateUserDTO(
                "EMP-002", "testuser", "password123", Role.WORKER, Set.of());

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(request);
        });
        assertEquals("Username already exists.", ex.getMessage());
    }

    @Test
    void createUser_ThrowsWhenEmployeeIdExists() {
        CreateUserDTO request = new CreateUserDTO(
                "EMP-001", "newuser", "password123", Role.WORKER, Set.of());

        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmployeeId("EMP-001")).thenReturn(Optional.of(existingUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            userService.createUser(request);
        });
        assertEquals("Employee ID already exists.", ex.getMessage());
    }

    @Test
    void updateUser_Success() {
        UpdateUserDTO request = new UpdateUserDTO("EMP-001", "updateduser", Role.SUPERVISOR, Set.of(Permission.CAN_MANAGE_INVENTORY));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByUsername("updateduser")).thenReturn(Optional.empty()); // No conflict

        Optional<UserResponseDTO> response = userService.updateUser(userId, request);

        assertTrue(response.isPresent());
        assertEquals("updateduser", response.get().username());
        assertEquals(Role.SUPERVISOR, response.get().role());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateUser_ThrowsWhenUsernameConflict() {
        UpdateUserDTO request = new UpdateUserDTO("EMP-001", "anotheruser", Role.WORKER, Set.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        
        UserEntity otherUser = new UserEntity();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("anotheruser");
        when(userRepository.findByUsername("anotheruser")).thenReturn(Optional.of(otherUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            userService.updateUser(userId, request);
        });
        assertEquals("Username already exists.", ex.getMessage());
    }

    @Test
    void deactivateUser_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        Optional<UserResponseDTO> response = userService.deactivateUser(userId);

        assertTrue(response.isPresent());
        assertFalse(response.get().isActive());
        verify(userRepository).save(existingUser);
    }

    @Test
    void batchUpdatePermissions_ThrowsWhenUserMissing() {
        BatchPermissionDTO request = new BatchPermissionDTO(
                List.of(userId, UUID.randomUUID()), 
                Set.of(Permission.CAN_PICK), 
                BatchPermissionDTO.Mode.OVERWRITE
        );
        
        when(userRepository.findAllById(request.userIds())).thenReturn(List.of(existingUser)); // Only found 1 out of 2

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            userService.batchUpdatePermissions(request);
        });
        assertEquals("One or more user IDs were not found.", ex.getMessage());
    }
}
