package com.taskmanager.task_manager_api.service;

import com.taskmanager.task_manager_api.dto.PagedResponse;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    private static final String USER_A = "user-a-sub-123";
    private static final String USER_B = "user-b-sub-456";

    private Task userATask;
    private Task userBTask;

    @BeforeEach
    void setUp() {
        userATask = Task.builder()
                .id("task-aaa-111")
                .title("User A task")
                .description("Belongs to User A")
                .status(TaskStatus.TODO)
                .userId(USER_A)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userBTask = Task.builder()
                .id("task-bbb-222")
                .title("User B task")
                .status(TaskStatus.IN_PROGRESS)
                .userId(USER_B)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── createTask ────────────────────────────────────────────

    @Test
    void createTask_shouldReturnCreatedTask_withUserIdAndDeletedFalse() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("User A task");
        request.setDescription("Belongs to User A");

        when(taskRepository.save(any(Task.class))).thenReturn(userATask);

        TaskResponseDTO response = taskService.createTask(request, USER_A);

        assertThat(response.getTitle()).isEqualTo("User A task");
        assertThat(response.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(response.getUserId()).isEqualTo(USER_A);
        assertThat(response.isDeleted()).isFalse();
        verify(taskRepository, times(1)).save(any(Task.class));
    }

    // ── getAllTasks (paginated) ────────────────────────────────

    @Test
    void getAllTasks_shouldReturnPagedResponse_forCallerOnly() {
        PagedResponse<Task> mockPage = PagedResponse.of(
                List.of(userATask), null, 20);

        when(taskRepository.findAllByUserId(eq(USER_A), eq(20), isNull()))
                .thenReturn(mockPage);

        PagedResponse<TaskResponseDTO> response =
                taskService.getAllTasks(null, USER_A, 20, null);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getUserId()).isEqualTo(USER_A);
        assertThat(response.getNextToken()).isNull();  // no more pages
        assertThat(response.getCount()).isEqualTo(1);
    }

    @Test
    void getAllTasks_shouldUseStatusGSI_whenStatusProvided() {
        PagedResponse<Task> mockPage = PagedResponse.of(
                List.of(userATask), null, 20);

        when(taskRepository.findByUserIdAndStatus(
                eq(USER_A), eq(TaskStatus.TODO), eq(20), isNull()))
                .thenReturn(mockPage);

        PagedResponse<TaskResponseDTO> response =
                taskService.getAllTasks(TaskStatus.TODO, USER_A, 20, null);

        assertThat(response.getItems()).hasSize(1);
        // Verify correct GSI method was called (not the general findAllByUserId)
        verify(taskRepository, times(1))
                .findByUserIdAndStatus(USER_A, TaskStatus.TODO, 20, null);
        verify(taskRepository, never()).findAllByUserId(any(), anyInt(), any());
    }

    @Test
    void getAllTasks_shouldReturnNextToken_whenMorePagesExist() {
        PagedResponse<Task> mockPage = PagedResponse.of(
                List.of(userATask), "next-page-token-abc", 20);

        when(taskRepository.findAllByUserId(eq(USER_A), eq(20), isNull()))
                .thenReturn(mockPage);

        PagedResponse<TaskResponseDTO> response =
                taskService.getAllTasks(null, USER_A, 20, null);

        assertThat(response.getNextToken()).isEqualTo("next-page-token-abc");
    }

    @Test
    void getAllTasks_shouldCapLimit_atMaximum100() {
        PagedResponse<Task> mockPage = PagedResponse.of(List.of(), null, 100);
    
        // 999 gets capped to MAX_LIMIT=100
        when(taskRepository.findAllByUserId(eq(USER_A), eq(100), isNull()))
                .thenReturn(mockPage);
    
        PagedResponse<TaskResponseDTO> response =
                taskService.getAllTasks(null, USER_A, 999, null);
    
        // Confirm response limit is capped at 100
        assertThat(response.getLimit()).isEqualTo(100);
        verify(taskRepository).findAllByUserId(USER_A, 100, null);
    }

    // ── getTaskById ───────────────────────────────────────────

    @Test
    void getTaskById_shouldReturn_whenOwner() {
        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));

        TaskResponseDTO response = taskService.getTaskById("task-aaa-111", USER_A);

        assertThat(response.getId()).isEqualTo("task-aaa-111");
    }

    @Test
    void getTaskById_shouldThrow404_whenNotFound() {
        when(taskRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById("bad-id", USER_A))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void getTaskById_shouldThrow404_whenNotOwner() {
        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));

        assertThatThrownBy(() -> taskService.getTaskById("task-aaa-111", USER_B))
                .isInstanceOf(TaskAccessDeniedException.class);
    }

    // ── updateTask ────────────────────────────────────────────

    @Test
    void updateTask_shouldUpdate_whenOwner() {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Updated");
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
        request.setTitle("Sneaky");

        assertThatThrownBy(() -> taskService.updateTask("task-aaa-111", request, USER_B))
                .isInstanceOf(TaskAccessDeniedException.class);

        verify(taskRepository, never()).save(any());
    }

    // ── deleteTask (soft delete) ──────────────────────────────

    @Test
    void deleteTask_shouldSoftDelete_whenOwner() {
        Task softDeletedTask = Task.builder()
                .id("task-aaa-111")
                .title("User A task")
                .userId(USER_A)
                .deleted(true)
                .deletedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));
        when(taskRepository.softDelete("task-aaa-111")).thenReturn(softDeletedTask);

        TaskResponseDTO response = taskService.deleteTask("task-aaa-111", USER_A);

        // Phase 1: delete returns the task (not void)
        assertThat(response).isNotNull();
        assertThat(response.isDeleted()).isTrue();
        assertThat(response.getDeletedAt()).isNotNull();

        // Verify softDelete called (not hard deleteById)
        verify(taskRepository, times(1)).softDelete("task-aaa-111");
        verify(taskRepository, never()).deleteById(any());
    }

    @Test
    void deleteTask_shouldThrow404_whenNotOwner() {
        when(taskRepository.findById("task-aaa-111")).thenReturn(Optional.of(userATask));

        assertThatThrownBy(() -> taskService.deleteTask("task-aaa-111", USER_B))
                .isInstanceOf(TaskAccessDeniedException.class);

        // Verify neither soft nor hard delete was called
        verify(taskRepository, never()).softDelete(any());
        verify(taskRepository, never()).deleteById(any());
    }

    @Test
    void deleteTask_shouldThrow404_whenTaskNotFound() {
        when(taskRepository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.deleteTask("bad-id", USER_A))
                .isInstanceOf(TaskNotFoundException.class);
    }
}