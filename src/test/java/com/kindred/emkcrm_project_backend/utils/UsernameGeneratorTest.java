package com.kindred.emkcrm_project_backend.utils;

import com.kindred.emkcrm_project_backend.db.entities.User;
import com.kindred.emkcrm_project_backend.db.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsernameGeneratorTest {

    private StubUserRepository userRepository;
    private UsernameGenerator usernameGenerator;

    @BeforeEach
    void setUp() {
        userRepository = new StubUserRepository();
        usernameGenerator = new UsernameGenerator(userRepository.proxy());
    }

    @Test
    void generateUniqueUsernameReturnsBaseUsernameWhenItIsAvailable() {
        String username = usernameGenerator.generateUniqueUsername("Михаил", "Александрович", "Начинкин");

        assertThat(username).isEqualTo("m.nachinkin");
    }

    @Test
    void generateUniqueUsernameUsesMiddleInitialWhenBaseUsernameIsTaken() {
        userRepository.add("m.nachinkin");

        String username = usernameGenerator.generateUniqueUsername("Михаил", "Александрович", "Начинкин");

        assertThat(username).isEqualTo("m.a.nachinkin");
    }

    @Test
    void generateUniqueUsernameAppendsNumericSuffixWhenAllBaseVariantsAreTaken() {
        userRepository.add("m.nachinkin");
        userRepository.add("m.a.nachinkin");
        userRepository.add("m.a.nachinkin1");

        String username = usernameGenerator.generateUniqueUsername("Михаил", "Александрович", "Начинкин");

        assertThat(username).isEqualTo("m.a.nachinkin2");
    }

    @Test
    void generateUniqueUsernameRejectsEmptyFirstOrLastNameAfterNormalization() {
        assertThatThrownBy(() -> usernameGenerator.generateUniqueUsername("   ", null, "Начинкин"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("First name and last name must not be empty");
    }

    private static final class StubUserRepository implements InvocationHandler {

        private final Map<String, User> usersByUsername = new LinkedHashMap<>();

        UserRepository proxy() {
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class[]{UserRepository.class},
                    this
            );
        }

        void add(String username) {
            User user = new User();
            user.setUsername(username);
            usersByUsername.put(username, user);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "findByUsername" -> usersByUsername.get(args[0]);
                case "toString" -> "StubUserRepository";
                case "hashCode" -> System.identityHashCode(this);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Method not implemented in test stub: " + method.getName());
            };
        }
    }
}
