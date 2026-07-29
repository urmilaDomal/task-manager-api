package com.taskmanager.task_manager_api.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Wrapper for paginated API responses.
 *
 * Instead of returning a raw List<TaskResponseDTO>, every list endpoint
 * now returns this wrapper so clients know:
 *   - The current page of results (items)
 *   - Whether more pages exist (nextToken != null)
 *   - How many items are in this page (count)
 *
 * Usage:
 *   GET /api/v1/tasks?limit=20              → first page
 *   GET /api/v1/tasks?limit=20&nextToken=xyz → next page
 *
 * When nextToken is null → no more pages exist.
 *
 * Cursor-based pagination (not offset-based):
 *   Offset: "skip 20, take 20" — breaks if items inserted/deleted between requests
 *   Cursor: "give me items after this specific item" — stable regardless of
 *           concurrent inserts/deletes — correct approach for DynamoDB
 */
@Data
@Builder
public class PagedResponse<T> {

    private List<T> items;          // current page of results
    private int count;              // number of items in this page
    private String nextToken;       // opaque cursor for next page (null = no more pages)
    private int limit;              // page size requested

    public static <T> PagedResponse<T> of(List<T> items, String nextToken, int limit) {
        return PagedResponse.<T>builder()
                .items(items)
                .count(items.size())
                .nextToken(nextToken)
                .limit(limit)
                .build();
    }
}