package com.kindred.emkcrm_project_backend.services;

import com.kindred.emkcrm_project_backend.db.entities.IlliquidAssets;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.entities.warehouse.IlliquidAssetHistory;
import com.kindred.emkcrm_project_backend.db.entities.warehouse.IlliquidAssetHistoryOperationType;
import com.kindred.emkcrm_project_backend.db.repositories.IlliquidAssetHistoryRepository;
import com.kindred.emkcrm_project_backend.db.repositories.IlliquidAssetsRepository;
import com.kindred.emkcrm_project_backend.db.repositories.TenderRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.NotFoundException;
import com.kindred.emkcrm_project_backend.exception.UnauthorizedException;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetCloseRequest;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetCreateRequest;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetDetailsResponse;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetHistoryResponse;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetPageResponse;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetQuantityChangeRequest;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetSummaryResponse;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetUpdateRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class IlliquidAssetService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String CREATION_REASON = "CREATION";

    private final IlliquidAssetsRepository assetRepository;
    private final IlliquidAssetHistoryRepository historyRepository;
    private final TenderRepository tenderRepository;
    private final UserRepository userRepository;

    public IlliquidAssetService(
            IlliquidAssetsRepository assetRepository,
            IlliquidAssetHistoryRepository historyRepository,
            TenderRepository tenderRepository,
            UserRepository userRepository
    ) {
        this.assetRepository = assetRepository;
        this.historyRepository = historyRepository;
        this.tenderRepository = tenderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INVENTORY.NOLIQUID.VIEW')")
    public IlliquidAssetPageResponse list(
            String status,
            String q,
            Integer page,
            Integer size,
            String sort,
            String direction
    ) {
        Page<IlliquidAssets> result = assetRepository.findAll(
                buildSpecification(status, q),
                PageRequest.of(normalizePage(page), normalizeSize(size), resolveSort(sort, direction))
        );

        IlliquidAssetPageResponse response = new IlliquidAssetPageResponse();
        response.setItems(result.getContent().stream().map(this::toSummaryResponse).toList());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        return response;
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY.NOLIQUID.MANAGE')")
    public IlliquidAssetDetailsResponse create(IlliquidAssetCreateRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }

        String name = requireText(request.getName(), "name");
        String unit = requireText(request.getUnitOfMeasurement(), "unitOfMeasurement");
        float quantity = requirePositiveQuantity(request.getQuantity(), "quantity");
        User actor = currentUser();

        IlliquidAssets asset = new IlliquidAssets();
        asset.setCommodityMaterialValue(name);
        asset.setUnitsOfMeasurement(unit);
        asset.setQuantity(quantity);
        asset.setArrivalDate(LocalDate.now(ZoneOffset.UTC).toString());
        asset.setCreatedById(actor.getId());
        asset.setCommentary(trimToNull(request.getDescription()));
        asset.setAssetType("NOLIQUID");
        asset.setAssetStatus(STATUS_OPEN);

        IlliquidAssets saved = assetRepository.save(asset);
        saveHistory(saved, IlliquidAssetHistoryOperationType.CREATION, 0f, quantity, quantity, CREATION_REASON, null, null, actor);
        return toDetailsResponse(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INVENTORY.NOLIQUID.VIEW')")
    public IlliquidAssetDetailsResponse getById(Long id) {
        return toDetailsResponse(requireAsset(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY.NOLIQUID.MANAGE')")
    public IlliquidAssetDetailsResponse update(Long id, IlliquidAssetUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }

        IlliquidAssets asset = requireAssetForUpdate(id);
        if (request.getName() != null) {
            asset.setCommodityMaterialValue(requireText(request.getName(), "name"));
        }
        if (request.getUnitOfMeasurement() != null) {
            asset.setUnitsOfMeasurement(requireText(request.getUnitOfMeasurement(), "unitOfMeasurement"));
        }
        if (request.getDescription() != null) {
            asset.setCommentary(trimToNull(request.getDescription()));
        }

        return toDetailsResponse(assetRepository.save(asset));
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY.NOLIQUID.MANAGE')")
    public IlliquidAssetDetailsResponse changeQuantity(Long id, IlliquidAssetQuantityChangeRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }
        if (request.getQuantityDelta() == null || !Float.isFinite(request.getQuantityDelta()) || request.getQuantityDelta() == 0) {
            throw new BadRequestException("quantityDelta must not be zero");
        }

        IlliquidAssets asset = requireAssetForUpdate(id);
        if (STATUS_CLOSED.equals(normalizeStatus(asset.getAssetStatus()))) {
            throw new BadRequestException("Closed illiquid asset cannot be changed");
        }

        float oldQuantity = normalizeQuantity(asset.getQuantity());
        float delta = request.getQuantityDelta();
        float newQuantity = oldQuantity + delta;
        if (!Float.isFinite(newQuantity)) {
            throw new BadRequestException("quantity must be finite");
        }
        if (newQuantity < 0) {
            throw new BadRequestException("quantity cannot be less than zero");
        }

        User actor = currentUser();
        QuantityReason reason = delta > 0 ? resolveInflowReason(request) : resolveOutflowReason(request);
        Long tenderId = resolveTenderIdIfRequired(reason.reason(), request.getTenderLink());
        validateCommentRequirements(reason.reason(), request.getComment(), delta > 0);

        asset.setQuantity(newQuantity);
        if (newQuantity == 0) {
            asset.setAssetStatus(STATUS_CLOSED);
        }
        IlliquidAssets saved = assetRepository.save(asset);

        IlliquidAssetHistoryOperationType operationType = delta > 0
                ? IlliquidAssetHistoryOperationType.INCREASE
                : newQuantity == 0 ? IlliquidAssetHistoryOperationType.CLOSING : IlliquidAssetHistoryOperationType.DECREASE;
        saveHistory(saved, operationType, oldQuantity, newQuantity, delta, reason.reason(), request.getComment(), tenderId, actor);
        return toDetailsResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INVENTORY.NOLIQUID.MANAGE')")
    public IlliquidAssetDetailsResponse close(Long id, IlliquidAssetCloseRequest request) {
        if (request == null) {
            throw new BadRequestException("request must not be null");
        }

        IlliquidAssets asset = requireAssetForUpdate(id);
        if (STATUS_CLOSED.equals(normalizeStatus(asset.getAssetStatus()))) {
            throw new BadRequestException("Illiquid asset is already closed");
        }

        String reason = normalizeReason(enumValue(request.getReason()), "reason");
        validateOutflowReason(reason);
        validateCommentRequirements(reason, request.getComment(), false);
        Long tenderId = resolveTenderIdIfRequired(reason, request.getTenderLink());

        float oldQuantity = normalizeQuantity(asset.getQuantity());
        float delta = -oldQuantity;
        asset.setQuantity(0);
        asset.setAssetStatus(STATUS_CLOSED);

        User actor = currentUser();
        IlliquidAssets saved = assetRepository.save(asset);
        saveHistory(saved, IlliquidAssetHistoryOperationType.CLOSING, oldQuantity, 0, delta, reason, request.getComment(), tenderId, actor);
        return toDetailsResponse(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INVENTORY.NOLIQUID.VIEW')")
    public List<IlliquidAssetHistoryResponse> listHistory(Long assetId) {
        requireAsset(assetId);
        return historyRepository.findAllByAssetIdOrderByCreatedAtDescIdDesc(assetId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private Specification<IlliquidAssets> buildSpecification(String status, String q) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (trimToNull(status) != null) {
                String normalizedStatus = normalizeStatus(status);
                validateStatus(normalizedStatus);
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("assetStatus")),
                        normalizedStatus
                ));
            }
            if (trimToNull(q) != null) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("commodityMaterialValue")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("commentary")), pattern)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Sort resolveSort(String sort, String direction) {
        Sort.Direction resolvedDirection = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String resolvedSort = trimToNull(sort) == null ? "updatedAt" : sort.trim();
        return switch (resolvedSort) {
            case "id", "quantity", "createdAt", "updatedAt" -> Sort.by(resolvedDirection, resolvedSort);
            case "name" -> Sort.by(resolvedDirection, "commodityMaterialValue");
            case "status" -> Sort.by(resolvedDirection, "assetStatus");
            default -> Sort.by(resolvedDirection, "updatedAt");
        };
    }

    private QuantityReason resolveInflowReason(IlliquidAssetQuantityChangeRequest request) {
        String reason = normalizeReason(enumValue(request.getInflowReason()), "inflowReason");
        return new QuantityReason(validateInflowReason(reason));
    }

    private QuantityReason resolveOutflowReason(IlliquidAssetQuantityChangeRequest request) {
        String reason = normalizeReason(enumValue(request.getOutflowReason()), "outflowReason");
        return new QuantityReason(validateOutflowReason(reason));
    }

    private String validateInflowReason(String reason) {
        return switch (reason) {
            case "TENDER_PURCHASE", "INVENTORY_FOUND", "RETURN", "OTHER" -> reason;
            default -> throw new BadRequestException("Unknown inflowReason: " + reason);
        };
    }

    private String validateOutflowReason(String reason) {
        return switch (reason) {
            case "RETAIL_SALE", "TENDER_SALE", "DAMAGE_DISPOSAL", "EXPIRED_DISPOSAL", "OTHER" -> reason;
            default -> throw new BadRequestException("Unknown outflow reason: " + reason);
        };
    }

    private void validateCommentRequirements(String reason, String comment, boolean inflow) {
        if ("OTHER".equals(reason) && trimToNull(comment) == null) {
            throw new BadRequestException("comment is required for OTHER reason");
        }
        if (inflow && "TENDER_PURCHASE".equals(reason) && trimToNull(comment) == null) {
            throw new BadRequestException("comment is required for TENDER_PURCHASE reason");
        }
    }

    private Long resolveTenderIdIfRequired(String reason, String tenderLink) {
        if (!"TENDER_SALE".equals(reason) && !"TENDER_PURCHASE".equals(reason)) {
            return null;
        }
        String normalizedLink = requireText(tenderLink, "tenderLink");
        Long tenderId = extractTenderId(normalizedLink);
        if (!tenderRepository.existsById(tenderId)) {
            throw new NotFoundException("Tender not found: " + tenderId);
        }
        return tenderId;
    }

    private Long extractTenderId(String tenderLink) {
        Long fromUri = extractTenderIdFromUri(tenderLink);
        if (fromUri == null) {
            throw new BadRequestException("tenderLink must contain tender id");
        }
        return fromUri;
    }

    private Long extractTenderIdFromUri(String tenderLink) {
        try {
            URI uri = URI.create(tenderLink.trim());
            String query = uri.getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] nameValue = part.split("=", 2);
                    if (nameValue.length == 2 && "id".equalsIgnoreCase(nameValue[0]) && nameValue[1].matches("\\d+")) {
                        return Long.parseLong(nameValue[1]);
                    }
                }
            }
            String path = uri.getPath();
            Long fromPath = extractLastNumericSegment(path);
            if (fromPath != null) {
                return fromPath;
            }
            return extractLastNumericSegment(uri.getFragment());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private IlliquidAssetHistory saveHistory(
            IlliquidAssets asset,
            IlliquidAssetHistoryOperationType operationType,
            Float oldQuantity,
            float newQuantity,
            float quantityDelta,
            String reason,
            String comment,
            Long tenderId,
            User actor
    ) {
        IlliquidAssetHistory history = new IlliquidAssetHistory();
        history.setAsset(asset);
        history.setOperationType(operationType);
        history.setOldQuantity(oldQuantity);
        history.setNewQuantity(newQuantity);
        history.setQuantityDelta(quantityDelta);
        history.setReason(reason);
        history.setComment(trimToNull(comment));
        history.setRelatedTenderId(tenderId);
        history.setChangedById(actor.getId());
        history.setChangedByUsername(actor.getUsername());
        return historyRepository.save(history);
    }

    private IlliquidAssets requireAsset(Long id) {
        if (id == null) {
            throw new BadRequestException("asset id is required");
        }
        return assetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Illiquid asset not found: " + id));
    }

    private IlliquidAssets requireAssetForUpdate(Long id) {
        if (id == null) {
            throw new BadRequestException("asset id is required");
        }
        return assetRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Illiquid asset not found: " + id));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Unauthorized");
        }
        User user = userRepository.findByUsername(authentication.getName());
        if (user == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return user;
    }

    private int normalizePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        return size;
    }

    private float requireNonNegativeQuantity(Float quantity, String fieldName) {
        if (quantity == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        if (!Float.isFinite(quantity) || quantity < 0) {
            throw new BadRequestException(fieldName + " must be >= 0");
        }
        return quantity;
    }

    private float requirePositiveQuantity(Float quantity, String fieldName) {
        float normalizedQuantity = requireNonNegativeQuantity(quantity, fieldName);
        if (normalizedQuantity == 0) {
            throw new BadRequestException(fieldName + " must be > 0");
        }
        return normalizedQuantity;
    }

    private float normalizeQuantity(float quantity) {
        return Math.max(quantity, 0);
    }

    private String normalizeReason(String value, String fieldName) {
        if (trimToNull(value) == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        return trimToNull(status) == null ? STATUS_OPEN : status.trim().toUpperCase(Locale.ROOT);
    }

    private void validateStatus(String status) {
        if (!STATUS_OPEN.equals(status) && !STATUS_CLOSED.equals(status)) {
            throw new BadRequestException("status must be one of OPEN, CLOSED");
        }
    }

    private Long extractLastNumericSegment(String path) {
        if (path == null) {
            return null;
        }
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (segments[i].matches("\\d+")) {
                return Long.parseLong(segments[i]);
            }
        }
        return null;
    }

    private String requireText(String value, String fieldName) {
        if (trimToNull(value) == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String enumValue(Object value) {
        return value == null ? null : value.toString();
    }

    private IlliquidAssetSummaryResponse toSummaryResponse(IlliquidAssets asset) {
        IlliquidAssetSummaryResponse response = new IlliquidAssetSummaryResponse();
        response.setId(asset.getId());
        response.setName(asset.getCommodityMaterialValue());
        response.setUnitOfMeasurement(asset.getUnitsOfMeasurement());
        response.setQuantity(asset.getQuantity());
        response.setDescription(asset.getCommentary());
        response.setStatus(IlliquidAssetSummaryResponse.StatusEnum.fromValue(normalizeStatus(asset.getAssetStatus())));
        response.setCreatedAt(toOffsetDateTime(asset.getCreatedAt()));
        response.setUpdatedAt(toOffsetDateTime(asset.getUpdatedAt()));
        return response;
    }

    private IlliquidAssetDetailsResponse toDetailsResponse(IlliquidAssets asset) {
        IlliquidAssetSummaryResponse summary = toSummaryResponse(asset);
        IlliquidAssetDetailsResponse response = new IlliquidAssetDetailsResponse();
        response.setId(summary.getId());
        response.setName(summary.getName());
        response.setUnitOfMeasurement(summary.getUnitOfMeasurement());
        response.setQuantity(summary.getQuantity());
        response.setDescription(summary.getDescription());
        response.setStatus(IlliquidAssetDetailsResponse.StatusEnum.fromValue(summary.getStatus().getValue()));
        response.setCreatedAt(summary.getCreatedAt());
        response.setUpdatedAt(summary.getUpdatedAt());
        return response;
    }

    private IlliquidAssetHistoryResponse toHistoryResponse(IlliquidAssetHistory history) {
        IlliquidAssetHistoryResponse response = new IlliquidAssetHistoryResponse();
        response.setId(history.getId());
        response.setAssetId(history.getAsset().getId());
        response.setOperationType(IlliquidAssetHistoryResponse.OperationTypeEnum.fromValue(history.getOperationType().name()));
        response.setOldQuantity(history.getOldQuantity());
        response.setNewQuantity(history.getNewQuantity());
        response.setQuantityDelta(history.getQuantityDelta());
        response.setReason(history.getReason());
        response.setComment(history.getComment());
        response.setRelatedTenderId(history.getRelatedTenderId());
        response.setChangedById(history.getChangedById());
        response.setChangedByUsername(history.getChangedByUsername());
        response.setCreatedAt(toOffsetDateTime(history.getCreatedAt()));
        return response;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : OffsetDateTime.of(value, ZoneOffset.UTC);
    }

    private record QuantityReason(String reason) {
    }
}
