package com.taskmanager.task_manager_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanager.task_manager_api.dto.TaskRequestDTO;
import com.taskmanager.task_manager_api.dto.TaskResponseDTO;
import com.taskmanager.task_manager_api.exception.GlobalExceptionHandler;
import com.taskmanager.task_manager_api.exception.TaskAccessDeniedException;
import com.taskmanager.task_manager_api.exception.TaskNotFoundException;
import com.taskmanager.task_manager_api.model.TaskStatus;
import com.taskmanager.task_manager_api.service.TaskService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    // ─────────────────────────────────────────────────────────
    // A real JWT has 3 base64url parts: header.payload.signature
    // We build a fake one with a known 'sub' claim so JwtUtil
    // can extract the userId without hitting real Cognito.
    // API Gateway's signature verification is bypassed in tests
    // since we're testing the controller layer, not AWS auth.
    // ─────────────────────────────────────────────────────────
    private static final String TEST_USER_ID = "test-user-sub-abc-123";
    private static final String FAKE_JWT = buildFakeJwt(TEST_USER_ID);

    private static String buildFakeJwt(String sub) {
        // Header (standard JWT header — algorithm doesn't matter for our test)
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());

        // Payload — contains the 'sub' claim our JwtUtil reads
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + sub + "\",\"email\":\"test@example.com\"}").getBytes());

        // Signature — fake, not verified in unit tests
        String signature = "fakesignature";

        return header + "." + payload + "." + signature;
    }

    private TaskResponseDTO sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = TaskResponseDTO.builder()
                .id("abc-123")
                .title("Learn AWS Lambda")
                .description("Deploy Spring Boot to Lambda")
                .status(TaskStatus.TODO)
                .userId(TEST_USER_ID)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/v1/tasks
    // ─────────────────────────────────────────────────────────

    @Test
    void createTask_shouldReturn201_whenValidRequest() throws Exception {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Learn AWS Lambda");
        request.setDescription("Deploy Spring Boot to Lambda");

        when(taskService.createTask(any(TaskRequestDTO.class), eq(TEST_USER_ID)))
                .thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", FAKE_JWT)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("abc-123"))
                .andExpect(jsonPath("$.title").value("Learn AWS Lambda"))
                .andExpect(jsonPath("$.userId").value(TEST_USER_ID));
    }

    @Test
    void createTask_shouldReturn400_whenTitleIsBlank() throws Exception {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("");

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", FAKE_JWT)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verify(taskService, never()).createTask(any(), any());
    }

    @Test
    void createTask_shouldReturn400_whenTitleExceedsMaxLength() throws Exception {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("A".repeat(101));

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", FAKE_JWT)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).createTask(any(), any());
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/tasks
    // ─────────────────────────────────────────────────────────

    @Test
    void getAllTasks_shouldReturn200_withOnlyCallersTasks() throws Exception {
        when(taskService.getAllTasks(null, TEST_USER_ID))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(TEST_USER_ID));
    }

    @Test
    void getAllTasks_shouldReturn200_withStatusFilter() throws Exception {
        when(taskService.getAllTasks(TaskStatus.TODO, TEST_USER_ID))
                .thenReturn(List.of(sampleResponse));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", FAKE_JWT)
                        .param("status", "TODO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("TODO"));
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/v1/tasks/{id}
    // ─────────────────────────────────────────────────────────

    @Test
    void getTaskById_shouldReturn200_whenOwner() throws Exception {
        when(taskService.getTaskById("abc-123", TEST_USER_ID))
                .thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tasks/abc-123")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc-123"))
                .andExpect(jsonPath("$.userId").value(TEST_USER_ID));
    }

    @Test
    void getTaskById_shouldReturn404_whenTaskNotFound() throws Exception {
        when(taskService.getTaskById("bad-id", TEST_USER_ID))
                .thenThrow(new TaskNotFoundException("Task not found with id: bad-id"));

        mockMvc.perform(get("/api/v1/tasks/bad-id")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found with id: bad-id"));
    }

    @Test
    void getTaskById_shouldReturn404_whenNotOwner() throws Exception {
        // Service throws TaskAccessDeniedException when user doesn't own the task
        when(taskService.getTaskById("abc-123", TEST_USER_ID))
                .thenThrow(new TaskAccessDeniedException("abc-123"));

        mockMvc.perform(get("/api/v1/tasks/abc-123")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNotFound());
        // Returns 404 not 403 — intentional security decision
    }

    // ─────────────────────────────────────────────────────────
    // PUT /api/v1/tasks/{id}
    // ─────────────────────────────────────────────────────────

    @Test
    void updateTask_shouldReturn200_whenOwner() throws Exception {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Updated Title");
        request.setStatus(TaskStatus.IN_PROGRESS);

        TaskResponseDTO updated = TaskResponseDTO.builder()
                .id("abc-123")
                .title("Updated Title")
                .status(TaskStatus.IN_PROGRESS)
                .userId(TEST_USER_ID)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(taskService.updateTask(eq("abc-123"), any(TaskRequestDTO.class), eq(TEST_USER_ID)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/tasks/abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", FAKE_JWT)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateTask_shouldReturn404_whenNotOwner() throws Exception {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Sneaky update");

        when(taskService.updateTask(eq("abc-123"), any(TaskRequestDTO.class), eq(TEST_USER_ID)))
                .thenThrow(new TaskAccessDeniedException("abc-123"));

        mockMvc.perform(put("/api/v1/tasks/abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", FAKE_JWT)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateTask_shouldReturn400_whenTitleIsBlank() throws Exception {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("");

        mockMvc.perform(put("/api/v1/tasks/abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", FAKE_JWT)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).updateTask(any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────
    // DELETE /api/v1/tasks/{id}
    // ─────────────────────────────────────────────────────────

    @Test
    void deleteTask_shouldReturn204_whenOwner() throws Exception {
        doNothing().when(taskService).deleteTask("abc-123", TEST_USER_ID);

        mockMvc.perform(delete("/api/v1/tasks/abc-123")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNoContent());

        verify(taskService, times(1)).deleteTask("abc-123", TEST_USER_ID);
    }

    @Test
    void deleteTask_shouldReturn404_whenNotOwner() throws Exception {
        doThrow(new TaskAccessDeniedException("abc-123"))
                .when(taskService).deleteTask("abc-123", TEST_USER_ID);

        mockMvc.perform(delete("/api/v1/tasks/abc-123")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_shouldReturn404_whenTaskNotFound() throws Exception {
        doThrow(new TaskNotFoundException("Task not found with id: bad-id"))
                .when(taskService).deleteTask("bad-id", TEST_USER_ID);

        mockMvc.perform(delete("/api/v1/tasks/bad-id")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNotFound());
    }
}