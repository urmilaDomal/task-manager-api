package com.taskmanager.task_manager_api.service;

import com.taskmanager.task_manager_api.dto.TaskRequestDTO;
import com.taskmanager.task_manager_api.dto.TaskResponseDTO;
import com.taskmanager.task_manager_api.exception.TaskAccessDeniedException;
import com.taskmanager.task_manager_api.exception.TaskNotFoundException;
import com.taskmanager.task_manager_api.model.Task;
import com.taskmanager.task_manager_api.model.TaskStatus;
import com.taskmanager.task_manager_api.repository.TaskRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    // Two different users for ownership tests
    private static final String USER_A = "user-a-cognito-sub-123";
    private static final String USER_B = "user-b-cognito-sub-456";

    private Task userATask;
    private Task userBTask;

    @BeforeEach
    void setUp() {
        userATask = Task.builder()
                .id("task-aaa-111")
                .title("User A task")
                .description("Belongs to User A")
                .status(TaskStatus.TODO)
                .userId(USER_A)                     // ← owned by User A
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userBTask = Task.builder()
                .id("task-bbb-222")
                .title("User B task")
                .description("Belongs to User B")
                .status(TaskStatus.IN_PROGRESS)
                .userId(USER_B)                     // ← owned by User B
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // createTask
    // ─────────────────────────────────────────────────────────

    @Test
    void createTask_shouldReturnCreatedTask_withUserId() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("User A task");
        request.setDescription("Belongs to User A");

        when(taskRepository.save(any(Task.class))).thenReturn(userATask);

        TaskResponseDTO response = taskService.createTask(request, USER_A);

        assertThat(response.getTitle()).isEqualTo("User A task");
        assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(response.getUserId()).isEqualTo(USER_A);
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    // ─────────────────────────────────────────────────────────
    // getAllTasks
    // ─────────────────────────────────────────────────────────

    @Test
    void getAllTasks_shouldReturnOnlyCallersTasks() {
        // Repository returns tasks from BOTH users
        when(taskRepository.findAll()).thenReturn(List.of(userATask, userBTask));

        // User A calls — should only see their own task
        List<TaskResponseDTO> userATasks = taskService.getAllTasks(null, USER_A);

        assertThat(userATasks).hasSize(1);
        assertThat(userATasks.get(0).getUserId()).isEqualTo(USER_A);
        assertThat(userATasks.get(0).getTitle()).isEqualTo("User A task");
    }

    @Test
    void getAllTasks_shouldNotReturnOtherUsersTasks() {
        when(taskRepository.findAll()).thenReturn(List.of(userATask, userBTask));

        // User B calls — should NOT see User A's task
        List<TaskResponseDTO> userBTasks = taskService.getAllTasks(null, USER_B);

        assertThat(userBTasks).hasSize(1);
        assertThat(userBTasks.get(0).getUserId()).isEqualTo(USER_B);
        // Confirm User A's task is NOT in the result
        assertThat(userBTasks).noneMatch(t -> t.getUserId().equals(USER_A));
    }

    // ─────────────────────────────────────────────────────────
    // getTaskById
    // ─────────────────────────────────────────────────────────

    @Test
    void getTaskById_shouldReturnTask_whenOwner() {
        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));

        TaskResponseDTO response = taskService.getTaskById("task-aaa-111", USER_A);

        assertThat(response.getId()).isEqualTo("task-aaa-111");
        assertThat(response.getUserId()).isEqualTo(USER_A);
    }

    @Test
    void getTaskById_shouldThrow404_whenTaskNotFound() {
        when(taskRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById("bad-id", USER_A))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("bad-id");
    }

    @Test
    void getTaskById_shouldThrow404_whenNotOwner() {
        // Task exists but belongs to User A — User B tries to access it
        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));

        assertThatThrownBy(() -> taskService.getTaskById("task-aaa-111", USER_B))
                .isInstanceOf(TaskAccessDeniedException.class);
        // Returns 404 (not 403) — intentional, see TaskAccessDeniedException javadoc
    }

    // ─────────────────────────────────────────────────────────
    // updateTask
    // ─────────────────────────────────────────────────────────

    @Test
    void updateTask_shouldUpdate_whenOwner() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Updated title");
        request.setStatus(TaskStatus.IN_PROGRESS);

        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));
        when(taskRepository.save(any(Task.class))).thenReturn(userATask);

        TaskResponseDTO response = taskService.updateTask("task-aaa-111", request, USER_A);

        assertThat(response).isNotNull();
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    @Test
    void updateTask_shouldThrow404_whenNotOwner() {
        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));

        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Sneaky update");

        assertThatThrownBy(() -> taskService.updateTask("task-aaa-111", request, USER_B))
                .isInstanceOf(TaskAccessDeniedException.class);

        // Verify save was never called — update was blocked
        verify(taskRepository, never()).save(any(Task.class));
    }

    // ─────────────────────────────────────────────────────────
    // deleteTask
    // ─────────────────────────────────────────────────────────

    @Test
    void deleteTask_shouldDelete_whenOwner() {
        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));

        taskService.deleteTask("task-aaa-111", USER_A);

        verify(taskRepository, times(1)).deleteById("task-aaa-111");
    }

    @Test
    void deleteTask_shouldThrow404_whenNotOwner() {
        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));

        assertThatThrownBy(() -> taskService.deleteTask("task-aaa-111", USER_B))
                .isInstanceOf(TaskAccessDeniedException.class);

        // Verify deleteById was never called — delete was blocked
        verify(taskRepository, never()).deleteById(any());
    }

    @Test
    void deleteTask_shouldThrow404_whenTaskNotFound() {
        when(taskRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteTask("bad-id", USER_A))
                .isInstanceOf(TaskNotFoundException.class);
    }
}