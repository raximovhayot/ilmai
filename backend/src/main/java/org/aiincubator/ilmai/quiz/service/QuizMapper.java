package org.aiincubator.ilmai.quiz.service;

import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.quiz.domain.QuizQuestion;
import org.aiincubator.ilmai.quiz.domain.QuizSession;
import org.aiincubator.ilmai.quiz.payload.QuizQuestionResponse;
import org.aiincubator.ilmai.quiz.payload.QuizSessionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class QuizMapper {

    protected MaterialsApi materialsApi;

    @Autowired
    public void setMaterialsApi(MaterialsApi materialsApi) {
        this.materialsApi = materialsApi;
    }

    @Mapping(target = "topicId", source = "topicId")
    @Mapping(target = "difficulty", expression = "java(session.getDifficulty() != null ? session.getDifficulty().name() : null)")
    @Mapping(target = "locale", expression = "java(session.getLocale() != null ? session.getLocale().name() : null)")
    @Mapping(target = "status", expression = "java(session.getStatus() != null ? session.getStatus().name() : null)")
    public abstract QuizSessionResponse toResponse(QuizSession session);

    @Mapping(target = "type", expression = "java(question.getType() != null ? question.getType().name() : null)")
    @Mapping(target = "materialId", source = "materialId")
    @Mapping(target = "materialName", expression = "java(resolveMaterialName(question.getMaterialId()))")
    public abstract QuizQuestionResponse toResponse(QuizQuestion question);

    protected String resolveMaterialName(UUID materialId) {
        if (materialId == null) {
            return null;
        }
        return materialsApi.findById(materialId).map(MaterialDto::getTitle).orElse(null);
    }
}
