package com.agilerisk.service.ai;

import com.agilerisk.dto.ai.ModelOutcome;

public interface AiModelClient<REQ> {
    ModelOutcome predict(Long sprintId, REQ request);
}
