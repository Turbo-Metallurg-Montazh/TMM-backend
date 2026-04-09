package com.kindred.emkcrm_project_backend.services;

import com.kindred.emkcrm_project_backend.authentication.UserService;
import com.kindred.emkcrm_project_backend.authentication.rbac.RbacService;
import com.kindred.emkcrm_project_backend.db.entities.TenderFilter;
import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.TenderFilterRepository;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import com.kindred.emkcrm_project_backend.exception.BadRequestException;
import com.kindred.emkcrm_project_backend.exception.ConflictException;
import com.kindred.emkcrm_project_backend.model.AddTenderFilterRequest;
import com.kindred.emkcrm_project_backend.model.ParseTenderFilterRequest;
import com.kindred.emkcrm_project_backend.utils.json.TenderJsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenderFilterManagementServiceTest {

    private StubTenderFilterRepository tenderFilterRepository;
    private StubUserService userService;
    private TenderFilterManagementService tenderFilterManagementService;

    @BeforeEach
    void setUp() {
        tenderFilterRepository = new StubTenderFilterRepository();
        userService = new StubUserService();
        tenderFilterManagementService = new TenderFilterManagementService(
                tenderFilterRepository.proxy(),
                new TenderJsonMapper(),
                userService,
                new TenderFilterUrlParser()
        );

        User currentUser = new User();
        currentUser.setId(7L);
        currentUser.setUsername("alice");
        userService.setUser("alice", currentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("alice", "pwd"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addTenderFilterSavesNormalizedNameAndDefaultsActive() {
        AddTenderFilterRequest request = new AddTenderFilterRequest();
        request.setName("  Demo Filter  ");
        request.setText(List.of("search phrase"));

        String result = tenderFilterManagementService.addTenderFilter(request);

        assertThat(result).isEqualTo("Фильтр для тендеров успешно добавлен в БД");
        TenderFilter savedFilter = tenderFilterRepository.requireByName("Demo Filter");
        assertThat(savedFilter.getUserId()).isEqualTo(7L);
        assertThat(savedFilter.isActive()).isTrue();
        assertThat(savedFilter.getText()).containsExactly("search phrase");
        assertThat(savedFilter.getDateTimeFrom()).isNull();
        assertThat(savedFilter.getDateTimeTo()).isNull();
    }

    @Test
    void addTenderFilterRejectsDuplicatePayloadForCurrentUser() {
        TenderFilter existingFilter = new TenderFilter();
        existingFilter.setId(11L);
        existingFilter.setName("existing-filter");
        existingFilter.setUserId(7L);
        existingFilter.setActive(true);
        existingFilter.setText(new String[]{"search phrase"});
        tenderFilterRepository.add(existingFilter);

        AddTenderFilterRequest request = new AddTenderFilterRequest();
        request.setName("new-filter");
        request.setText(List.of("search phrase"));

        assertThatThrownBy(() -> tenderFilterManagementService.addTenderFilter(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Фильтр уже существует под названием existing-filter");
    }

    @Test
    void parseTenderFilterGeneratesNameFromTextAndPersistsParsedFields() {
        ParseTenderFilterRequest request = new ParseTenderFilterRequest();
        request.setName(" ");
        request.setFilter("https://zakupki.kontur.ru/Grid?q.Inns=2312159262&q.SortOrder=2&q.Text=abcdefghijklmnopqrstuvwxyz");

        String result = tenderFilterManagementService.parseTenderFilter(request);

        assertThat(result).isEqualTo("abcdefghijklmnopqrstuvwx");
        TenderFilter savedFilter = tenderFilterRepository.requireByName("abcdefghijklmnopqrstuvwx");
        assertThat(savedFilter.getIncludeInns()).containsExactly("2312159262");
        assertThat(savedFilter.getSortOrder()).isEqualTo(2);
        assertThat(savedFilter.getText()).containsExactly("abcdefghijklmnopqrstuvwxyz");
        assertThat(savedFilter.getDateTimeFrom()).isNull();
        assertThat(savedFilter.getDateTimeTo()).isNull();
    }

    @Test
    void updateTenderFilterByNameRejectsInvalidPublishDateRange() {
        TenderFilter existingFilter = new TenderFilter();
        existingFilter.setId(15L);
        existingFilter.setName("existing-filter");
        existingFilter.setUserId(7L);
        existingFilter.setActive(true);
        tenderFilterRepository.add(existingFilter);

        AddTenderFilterRequest request = new AddTenderFilterRequest();
        request.setName("existing-filter");
        request.setDateTimeFrom(OffsetDateTime.parse("2026-04-09T10:00:00Z"));
        request.setDateTimeTo(OffsetDateTime.parse("2026-04-08T10:00:00Z"));

        assertThatThrownBy(() -> tenderFilterManagementService.updateTenderFilterByName("existing-filter", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("dateTimeFrom must be <= dateTimeTo");
    }

    private static final class StubUserService extends UserService {

        private final Map<String, User> usersByUsername = new LinkedHashMap<>();

        StubUserService() {
            super((UserRepository) null, (RbacService) null, (PasswordEncoder) null);
        }

        void setUser(String username, User user) {
            usersByUsername.put(username, user);
        }

        @Override
        public User findUserWithRolesByUsername(String username) {
            return usersByUsername.get(username);
        }
    }

    private static final class StubTenderFilterRepository implements InvocationHandler {

        private final Map<Long, TenderFilter> filtersById = new LinkedHashMap<>();
        private long nextId = 1L;

        TenderFilterRepository proxy() {
            return (TenderFilterRepository) Proxy.newProxyInstance(
                    TenderFilterRepository.class.getClassLoader(),
                    new Class[]{TenderFilterRepository.class},
                    this
            );
        }

        void add(TenderFilter tenderFilter) {
            if (tenderFilter.getId() == null) {
                tenderFilter.setId(nextId++);
            } else {
                nextId = Math.max(nextId, tenderFilter.getId() + 1);
            }
            filtersById.put(tenderFilter.getId(), tenderFilter);
        }

        TenderFilter requireByName(String name) {
            return filtersById.values().stream()
                    .filter(filter -> name.equals(filter.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Filter not found: " + name));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "save" -> save((TenderFilter) args[0]);
                case "findByName" -> findByName((String) args[0]);
                case "existsByName" -> existsByName((String) args[0]);
                case "existsByNameAndIdNot" -> existsByNameAndIdNot((String) args[0], (Long) args[1]);
                case "findAllByUserId" -> findAllByUserId((Long) args[0]);
                case "findAll" -> findAll();
                case "delete" -> delete((TenderFilter) args[0]);
                case "toString" -> "StubTenderFilterRepository";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Method not implemented in test stub: " + method.getName());
            };
        }

        private TenderFilter save(TenderFilter tenderFilter) {
            if (tenderFilter.getId() == null) {
                tenderFilter.setId(nextId++);
            }
            filtersById.put(tenderFilter.getId(), tenderFilter);
            return tenderFilter;
        }

        private Optional<TenderFilter> findByName(String name) {
            return filtersById.values().stream()
                    .filter(filter -> name.equals(filter.getName()))
                    .findFirst();
        }

        private boolean existsByName(String name) {
            return filtersById.values().stream().anyMatch(filter -> name.equals(filter.getName()));
        }

        private boolean existsByNameAndIdNot(String name, Long id) {
            return filtersById.values().stream()
                    .anyMatch(filter -> name.equals(filter.getName()) && !filter.getId().equals(id));
        }

        private Iterable<TenderFilter> findAllByUserId(Long userId) {
            return filtersById.values().stream()
                    .filter(filter -> userId.equals(filter.getUserId()))
                    .toList();
        }

        private List<TenderFilter> findAll() {
            List<TenderFilter> filters = new ArrayList<>(filtersById.values());
            filters.sort(Comparator.comparing(TenderFilter::getName));
            return filters;
        }

        private Object delete(TenderFilter tenderFilter) {
            filtersById.remove(tenderFilter.getId());
            return null;
        }
    }
}
