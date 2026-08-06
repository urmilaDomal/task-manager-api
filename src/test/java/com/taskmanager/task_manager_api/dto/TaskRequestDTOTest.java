package com.taskmanager.task_manager_api.dto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TaskRequestDTOTest {

    @Test
    void setTitle_shouldSanitizeXss() {
        TaskRequestDTO dto = new TaskRequestDTO();
        dto.setTitle("<script>alert('xss')</script>My Task");
        assertThat(dto.getTitle())
                .doesNotContain("<script>")
                .contains("My Task");
    }

    @Test
    void setDescription_shouldSanitizeXss() {
        TaskRequestDTO dto = new TaskRequestDTO();
        dto.setDescription("<img onclick=\"steal()\">Normal description");
        assertThat(dto.getDescription())
                .doesNotContain("onclick")
                .contains("Normal description");
    }

    @Test
    void setTitle_shouldPreserveNormalText() {
        TaskRequestDTO dto = new TaskRequestDTO();
        dto.setTitle("Buy groceries");
        assertThat(dto.getTitle()).isEqualTo("Buy groceries");
    }

    @Test
    void setDescription_shouldPreserveNormalText() {
        TaskRequestDTO dto = new TaskRequestDTO();
        dto.setDescription("Remember to call John at 5pm");
        assertThat(dto.getDescription()).isEqualTo("Remember to call John at 5pm");
    }

    @Test
    void setTitle_shouldHandleNull() {
        TaskRequestDTO dto = new TaskRequestDTO();
        dto.setTitle(null);
        assertThat(dto.getTitle()).isNull();
    }

    @Test
    void setDescription_shouldHandleNull() {
        TaskRequestDTO dto = new TaskRequestDTO();
        dto.setDescription(null);
        assertThat(dto.getDescription()).isNull();
    }
}