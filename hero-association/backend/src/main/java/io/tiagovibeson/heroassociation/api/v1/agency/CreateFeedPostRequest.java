package io.tiagovibeson.heroassociation.api.v1.agency;

import java.util.UUID;

import io.tiagovibeson.heroassociation.domain.FeedPostAuthorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateFeedPostRequest(
        @NotNull FeedPostAuthorType authorType,
        @NotNull UUID authorId,
        @NotBlank @Size(max = 500) String content,
        UUID itemId,
        @Positive Integer itemQuantity) {
}
