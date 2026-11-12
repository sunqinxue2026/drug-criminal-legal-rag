package com.gjq.dto;

import com.gjq.entity.Item;
import lombok.Data;

@Data
public class ItemPreviewResponse {
    private Item item;
    private int charCount;
    private boolean supported;
    private String message;
}
