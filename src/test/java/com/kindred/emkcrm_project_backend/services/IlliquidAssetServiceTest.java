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
import com.kindred.emkcrm_project_backend.model.IlliquidAssetCloseRequest;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetCreateRequest;
import com.kindred.emkcrm_project_backend.model.IlliquidAssetQuantityChangeRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IlliquidAssetServiceTest {

    @Mock
    private IlliquidAssetsRepository assetRepository;

    @Mock
    private IlliquidAssetHistoryRepository historyRepository;

    @Mock
    private TenderRepository tenderRepository;

    @Mock
    private UserRepository userRepository;

    private IlliquidAssetService service;

    @BeforeEach
    void setUp() {
        service = new IlliquidAssetService(assetRepository, historyRepository, tenderRepository, userRepository);
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("warehouse.user");
        lenient().when(userRepository.findByUsername("warehouse.user")).thenReturn(actor);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("warehouse.user", "password")
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSavesOpenAssetAndCreationHistory() {
        IlliquidAssetCreateRequest request = new IlliquidAssetCreateRequest(
                "Кабель",
                "м",
                12.5f,
                IlliquidAssetCreateRequest.InflowReasonEnum.INVENTORY_FOUND
        );
        request.setDescription("Остаток бухты");
        when(assetRepository.save(any(IlliquidAssets.class))).thenAnswer(invocation -> {
            IlliquidAssets saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        var response = service.create(request);

        ArgumentCaptor<IlliquidAssetHistory> historyCaptor = ArgumentCaptor.forClass(IlliquidAssetHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        IlliquidAssetHistory history = historyCaptor.getValue();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getStatus().getValue()).isEqualTo("OPEN");
        assertThat(history.getOperationType()).isEqualTo(IlliquidAssetHistoryOperationType.CREATION);
        assertThat(history.getOldQuantity()).isEqualTo(0);
        assertThat(history.getNewQuantity()).isEqualTo(12.5f);
        assertThat(history.getQuantityDelta()).isEqualTo(12.5f);
        assertThat(history.getReason()).isEqualTo("INVENTORY_FOUND");
        assertThat(history.getChangedById()).isEqualTo(7L);
        assertThat(history.getChangedByUsername()).isEqualTo("warehouse.user");
    }

    @Test
    void createRejectsZeroQuantityBecauseClosedItemCannotBeCreatedWithoutDisposalReason() {
        IlliquidAssetCreateRequest request = new IlliquidAssetCreateRequest(
                "Кабель",
                "м",
                0f,
                IlliquidAssetCreateRequest.InflowReasonEnum.INVENTORY_FOUND
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("quantity must be > 0");

        verify(assetRepository, never()).save(any());
    }

    @Test
    void decreaseToZeroAutomaticallyClosesAssetAndSavesClosingReason() {
        IlliquidAssets asset = openAsset(10L, 5f);
        when(assetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(asset));
        when(assetRepository.save(asset)).thenReturn(asset);

        IlliquidAssetQuantityChangeRequest request = new IlliquidAssetQuantityChangeRequest(-5f);
        request.setOutflowReason(IlliquidAssetQuantityChangeRequest.OutflowReasonEnum.RETAIL_SALE);

        var response = service.changeQuantity(10L, request);

        ArgumentCaptor<IlliquidAssetHistory> historyCaptor = ArgumentCaptor.forClass(IlliquidAssetHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        IlliquidAssetHistory history = historyCaptor.getValue();
        assertThat(response.getQuantity()).isZero();
        assertThat(response.getStatus().getValue()).isEqualTo("CLOSED");
        assertThat(history.getOperationType()).isEqualTo(IlliquidAssetHistoryOperationType.CLOSING);
        assertThat(history.getOldQuantity()).isEqualTo(5f);
        assertThat(history.getNewQuantity()).isZero();
        assertThat(history.getQuantityDelta()).isEqualTo(-5f);
        assertThat(history.getReason()).isEqualTo("RETAIL_SALE");
    }

    @Test
    void createFromTenderRequiresLinkAndCommentAndStoresOnlyTenderId() {
        IlliquidAssetCreateRequest request = new IlliquidAssetCreateRequest(
                "Кабель",
                "м",
                4f,
                IlliquidAssetCreateRequest.InflowReasonEnum.TENDER_PURCHASE
        );

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("tenderLink is required");

        request.setTenderLink("https://crm.example/tenders/42");
        when(tenderRepository.existsById(42L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("comment is required for TENDER_PURCHASE reason");

        request.setComment("Осталось после исполнения тендера");
        when(assetRepository.save(any(IlliquidAssets.class))).thenAnswer(invocation -> {
            IlliquidAssets saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        service.create(request);

        ArgumentCaptor<IlliquidAssetHistory> historyCaptor = ArgumentCaptor.forClass(IlliquidAssetHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getReason()).isEqualTo("TENDER_PURCHASE");
        assertThat(historyCaptor.getValue().getRelatedTenderId()).isEqualTo(42L);
        assertThat(historyCaptor.getValue().getComment()).isEqualTo("Осталось после исполнения тендера");
    }

    @Test
    void decreaseRejectsQuantityBelowZero() {
        when(assetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAsset(10L, 5f)));
        IlliquidAssetQuantityChangeRequest request = new IlliquidAssetQuantityChangeRequest(-5.1f);
        request.setOutflowReason(IlliquidAssetQuantityChangeRequest.OutflowReasonEnum.RETAIL_SALE);

        assertThatThrownBy(() -> service.changeQuantity(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("quantity cannot be less than zero");

        verify(assetRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void tenderPurchaseRequiresCommentAndStoresOnlyTenderIdFromLink() {
        IlliquidAssets asset = openAsset(10L, 5f);
        when(assetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(asset));

        IlliquidAssetQuantityChangeRequest request = new IlliquidAssetQuantityChangeRequest(2f);
        request.setInflowReason(IlliquidAssetQuantityChangeRequest.InflowReasonEnum.TENDER_PURCHASE);
        request.setTenderLink("https://crm.example/tenders/42");

        when(tenderRepository.existsById(42L)).thenReturn(true);
        assertThatThrownBy(() -> service.changeQuantity(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("comment is required for TENDER_PURCHASE reason");

        request.setComment("Не востребовано заказчиком");
        when(assetRepository.save(asset)).thenReturn(asset);
        service.changeQuantity(10L, request);

        ArgumentCaptor<IlliquidAssetHistory> historyCaptor = ArgumentCaptor.forClass(IlliquidAssetHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getRelatedTenderId()).isEqualTo(42L);
        assertThat(historyCaptor.getValue().getComment()).isEqualTo("Не востребовано заказчиком");
    }

    @Test
    void otherOutflowReasonRequiresComment() {
        when(assetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAsset(10L, 5f)));
        IlliquidAssetQuantityChangeRequest request = new IlliquidAssetQuantityChangeRequest(-1f);
        request.setOutflowReason(IlliquidAssetQuantityChangeRequest.OutflowReasonEnum.OTHER);

        assertThatThrownBy(() -> service.changeQuantity(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("comment is required for OTHER reason");

        verify(assetRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void tenderSaleRequiresLink() {
        when(assetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(openAsset(10L, 5f)));
        IlliquidAssetQuantityChangeRequest request = new IlliquidAssetQuantityChangeRequest(-1f);
        request.setOutflowReason(IlliquidAssetQuantityChangeRequest.OutflowReasonEnum.TENDER_SALE);

        assertThatThrownBy(() -> service.changeQuantity(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("tenderLink is required");

        verify(assetRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void closeRejectsAlreadyClosedAsset() {
        IlliquidAssets asset = openAsset(10L, 0);
        asset.setAssetStatus("CLOSED");
        when(assetRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(asset));
        IlliquidAssetCloseRequest request = new IlliquidAssetCloseRequest(IlliquidAssetCloseRequest.ReasonEnum.RETAIL_SALE);

        assertThatThrownBy(() -> service.close(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Illiquid asset is already closed");

        verify(assetRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    private IlliquidAssets openAsset(Long id, float quantity) {
        IlliquidAssets asset = new IlliquidAssets();
        asset.setId(id);
        asset.setCommodityMaterialValue("Кабель");
        asset.setUnitsOfMeasurement("м");
        asset.setQuantity(quantity);
        asset.setAssetStatus("OPEN");
        return asset;
    }
}
