package org.aiincubator.ilmai.ai;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultRetrievalApiTest {

    @SuppressWarnings("unchecked")
    private DefaultRetrievalApi apiWith(VectorStore store) {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        return new DefaultRetrievalApi(provider, new RetrievalProperties());
    }

    @Test
    void retrieve_withRoomId_filtersByUserAndRoom() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        DefaultRetrievalApi api = apiWith(store);
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        api.retrieve(userId, roomId, "question");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        org.mockito.Mockito.verify(store).similaritySearch(captor.capture());
        Set<String> keys = keysIn(captor.getValue().getFilterExpression());
        assertThat(keys).contains("user_id", "room_id");
    }

    @Test
    void retrieve_withoutRoomId_filtersByUserOnly() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        DefaultRetrievalApi api = apiWith(store);
        UUID userId = UUID.randomUUID();

        api.retrieve(userId, "question");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        org.mockito.Mockito.verify(store).similaritySearch(captor.capture());
        Set<String> keys = keysIn(captor.getValue().getFilterExpression());
        assertThat(keys).containsExactly("user_id");
    }

    private static Set<String> keysIn(Filter.Expression expression) {
        Set<String> keys = new HashSet<>();
        collectKeys(expression, keys);
        return keys;
    }

    private static void collectKeys(Filter.Operand operand, Set<String> keys) {
        if (operand instanceof Filter.Key key) {
            keys.add(key.key());
        } else if (operand instanceof Filter.Expression expression) {
            List<Filter.Operand> operands = new ArrayList<>();
            if (expression.left() != null) {
                operands.add(expression.left());
            }
            if (expression.right() != null) {
                operands.add(expression.right());
            }
            for (Filter.Operand child : operands) {
                collectKeys(child, keys);
            }
        }
    }
}
