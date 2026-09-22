package io.tiagovibeson.heroassociation.application;

import java.util.UUID;

import io.tiagovibeson.heroassociation.api.v1.agency.AgencyStateResponse;
import io.tiagovibeson.heroassociation.application.exception.AgencyNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.HeroNotFoundException;
import io.tiagovibeson.heroassociation.application.exception.InvalidFeedPostAuthorException;
import io.tiagovibeson.heroassociation.application.exception.InvalidFeedPostItemException;
import io.tiagovibeson.heroassociation.domain.Agency;
import io.tiagovibeson.heroassociation.domain.AgencyItem;
import io.tiagovibeson.heroassociation.domain.FeedPost;
import io.tiagovibeson.heroassociation.domain.FeedPostAuthorType;
import io.tiagovibeson.heroassociation.domain.Hero;
import io.tiagovibeson.heroassociation.domain.Item;
import io.tiagovibeson.heroassociation.repository.AgencyRepository;
import io.tiagovibeson.heroassociation.repository.AgencyItemRepository;
import io.tiagovibeson.heroassociation.repository.FeedPostRepository;
import io.tiagovibeson.heroassociation.repository.HeroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FeedPostService {

    @Inject
    AgencyRepository agencyRepository;

    @Inject
    HeroRepository heroRepository;

    @Inject
    FeedPostRepository feedPostRepository;

    @Inject
    AgencyItemRepository agencyItemRepository;

    @Inject
    AgencyStateService agencyStateService;

    @Transactional
    public AgencyStateResponse createPost(
            UUID agencyId,
            FeedPostAuthorType authorType,
            UUID authorId,
            String content,
            UUID itemId,
            Integer itemQuantity) {
        Agency agency = agencyRepository.findByIdOptional(agencyId)
                .orElseThrow(() -> new AgencyNotFoundException(agencyId));
        String authorName = authorNameFor(agency, authorType, authorId);
        ItemAttachment itemAttachment = itemAttachmentFor(agency, itemId, itemQuantity);
        feedPostRepository.persist(new FeedPost(
                agency,
                authorType,
                authorId,
                authorName,
                content.trim(),
                itemAttachment.item(),
                itemAttachment.quantity()));
        return agencyStateService.findState(agencyId);
    }

    private ItemAttachment itemAttachmentFor(Agency agency, UUID itemId, Integer itemQuantity) {
        if (itemId == null && itemQuantity == null) {
            return new ItemAttachment(null, null);
        }
        if (itemId == null || itemQuantity == null) {
            throw new InvalidFeedPostItemException("An item attachment requires an item and quantity.");
        }
        if (itemQuantity < 1) {
            throw new InvalidFeedPostItemException("An attached item quantity must be at least one.");
        }

        AgencyItem agencyItem = agencyItemRepository.find("agency.id = ?1 and item.id = ?2", agency.getId(), itemId)
                .firstResultOptional()
                .orElseThrow(() -> new InvalidFeedPostItemException("The attached item is not in this agency inventory."));
        if (itemQuantity > agencyItem.getQuantity()) {
            throw new InvalidFeedPostItemException("The attached item quantity exceeds the agency inventory.");
        }

        return new ItemAttachment(agencyItem.getItem(), itemQuantity);
    }

    private String authorNameFor(Agency agency, FeedPostAuthorType authorType, UUID authorId) {
        return switch (authorType) {
            case AGENCY -> agencyNameFor(agency, authorId);
            case MANAGER -> managerNameFor(agency, authorId);
            case HERO -> heroNameFor(agency, authorId);
        };
    }

    private String agencyNameFor(Agency agency, UUID authorId) {
        if (!agency.getId().equals(authorId)) {
            throw new InvalidFeedPostAuthorException("An agency post must use the agency as its author.");
        }
        return agency.getName();
    }

    private String managerNameFor(Agency agency, UUID authorId) {
        if (!agency.getLeader().getId().equals(authorId)) {
            throw new InvalidFeedPostAuthorException("Only the agency leader can publish as its manager.");
        }
        return agency.getLeader().getDisplayName();
    }

    private String heroNameFor(Agency agency, UUID authorId) {
        Hero hero = heroRepository.find("id = ?1 and agency.id = ?2", authorId, agency.getId())
                .firstResultOptional()
                .orElseThrow(() -> new HeroNotFoundException(authorId));
        return hero.getName();
    }

    private record ItemAttachment(Item item, Integer quantity) {
    }
}
