package com.taskmanager.task_manager_api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanager.task_manager_api.dto.PagedResponse;
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

import static org.mockito.ArgumentMatchers.*;
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

    private static final String TEST_USER_ID = "test-user-sub-abc-123";
    private static final String FAKE_JWT = buildFakeJwt(TEST_USER_ID);

    private static String buildFakeJwt(String sub) {
        String header  = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"" + sub + "\",\"email\":\"test@example.com\"}").getBytes());
        return header + "." + payload + ".fakesignature";
    }

    private TaskResponseDTO sampleResponse;
    private PagedResponse<TaskResponseDTO> samplePage;

    @BeforeEach
    void setUp() {
        sampleResponse = TaskResponseDTO.builder()
                .id("abc-123")
                .title("Learn AWS Lambda")
                .description("Deploy Spring Boot to Lambda")
                .status(TaskStatus.TODO)
                .userId(TEST_USER_ID)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        samplePage = PagedResponse.of(List.of(sampleResponse), null, 20);
    }

    // ── POST /api/v1/tasks ────────────────────────────────────

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
                .andExpect(jsonPath("$.userId").value(TEST_USER_ID))
                .andExpect(jsonPath("$.deleted").value(false));
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

    // ── GET /api/v1/tasks (paginated) ─────────────────────────

    @Test
    void getAllTasks_shouldReturn200_withPagedResponse() throws Exception {
        when(taskService.getAllTasks(isNull(), eq(TEST_USER_ID), eq(20), isNull()))
                .thenReturn(samplePage);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.nextToken").doesNotExist());
    }

    @Test
    void getAllTasks_shouldReturn200_withNextToken_whenMorePagesExist() throws Exception {
        PagedResponse<TaskResponseDTO> pageWithToken =
                PagedResponse.of(List.of(sampleResponse), "next-page-cursor", 20);

        when(taskService.getAllTasks(isNull(), eq(TEST_USER_ID), eq(20), isNull()))
                .thenReturn(pageWithToken);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextToken").value("next-page-cursor"));
    }

    @Test
    void getAllTasks_shouldPassLimit_fromQueryParam() throws Exception {
        when(taskService.getAllTasks(isNull(), eq(TEST_USER_ID), eq(5), isNull()))
                .thenReturn(PagedResponse.of(List.of(), null, 5));

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", FAKE_JWT)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(5));
    }

    @Test
    void getAllTasks_shouldPassNextToken_fromQueryParam() throws Exception {
        when(taskService.getAllTasks(isNull(), eq(TEST_USER_ID), eq(20), eq("some-token")))
                .thenReturn(samplePage);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", FAKE_JWT)
                        .param("nextToken", "some-token"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllTasks_shouldFilterByStatus_whenProvided() throws Exception {
        when(taskService.getAllTasks(eq(TaskStatus.TODO), eq(TEST_USER_ID), eq(20), isNull()))
                .thenReturn(samplePage);

        mockMvc.perform(get("/api/v1/tasks")
                        .header("Authorization", FAKE_JWT)
                        .param("status", "TODO"))
                .andExpect(status().isOk());

        verify(taskService).getAllTasks(TaskStatus.TODO, TEST_USER_ID, 20, null);
    }

    // ── GET /api/v1/tasks/{id} ────────────────────────────────

    @Test
    void getTaskById_shouldReturn200_whenOwner() throws Exception {
        when(taskService.getTaskById("abc-123", TEST_USER_ID)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/tasks/abc-123")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc-123"));
    }

    @Test
    void getTaskById_shouldReturn404_whenNotFound() throws Exception {
        when(taskService.getTaskById("bad-id", TEST_USER_ID))
                .thenThrow(new TaskNotFoundException("Task not found with id: bad-id"));

        mockMvc.perform(get("/api/v1/tasks/bad-id")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTaskById_shouldReturn404_whenNotOwner() throws Exception {
        when(taskService.getTaskById("abc-123", TEST_USER_ID))
                .thenThrow(new TaskAccessDeniedException("abc-123"));

        mockMvc.perform(get("/api/v1/tasks/abc-123")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/v1/tasks/{id} ────────────────────────────────

    @Test
    void updateTask_shouldReturn200_whenOwner() throws Exception {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Updated");
        request.setStatus(TaskStatus.IN_PROGRESS);

        TaskResponseDTO updated = TaskResponseDTO.builder()
                .id("abc-123")
                .title("Updated")
                .status(TaskStatus.IN_PROGRESS)
                .userId(TEST_USER_ID)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(taskService.updateTask(eq("abc-123"), any(), eq(TEST_USER_ID)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/tasks/abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", FAKE_JWT)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateTask_shouldReturn404_whenNotOwner() throws Exception {
        TaskRequestDTO request = new TaskRequestDTO();
        request.setTitle("Sneaky");

        when(taskService.updateTask(eq("abc-123"), any(), eq(TEST_USER_ID)))
                .thenThrow(new TaskAccessDeniedException("abc-123"));

        mockMvc.perform(put("/api/v1/tasks/abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", FAKE_JWT)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/v1/tasks/{id} (soft delete) ───────────────

    @Test
    void deleteTask_shouldReturn200_withDeletedTask() throws Exception {
        // Phase 1: DELETE now returns 200 + deleted task (not 204 No Content)
        TaskResponseDTO deletedResponse = TaskResponseDTO.builder()
                .id("abc-123")
                .title("Learn AWS Lambda")
                .userId(TEST_USER_ID)
                .deleted(true)
                .deletedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(taskService.deleteTask("abc-123", TEST_USER_ID)).thenReturn(deletedResponse);

        mockMvc.perform(delete("/api/v1/tasks/abc-123")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isOk())                      // 200 not 204
                .andExpect(jsonPath("$.deleted").value(true))    // confirmed deleted
                .andExpect(jsonPath("$.deletedAt").exists());    // timestamp present
    }

    @Test
    void deleteTask_shouldReturn404_whenNotOwner() throws Exception {
        when(taskService.deleteTask("abc-123", TEST_USER_ID))
                .thenThrow(new TaskAccessDeniedException("abc-123"));

        mockMvc.perform(delete("/api/v1/tasks/abc-123")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_shouldReturn404_whenNotFound() throws Exception {
        when(taskService.deleteTask("bad-id", TEST_USER_ID))
                .thenThrow(new TaskNotFoundException("Task not found with id: bad-id"));

        mockMvc.perform(delete("/api/v1/tasks/bad-id")
                        .header("Authorization", FAKE_JWT))
                .andExpect(status().isNotFound());
    }
}