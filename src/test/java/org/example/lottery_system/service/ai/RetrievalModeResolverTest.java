package org.example.lottery_system.service.ai;

import org.example.lottery_system.service.ai.rag.RagProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 检索模式解析器测试:验证"远程向量可用 → simple;连不上外网 → 回落 framework"。
 *
 * 运行:mvnw.cmd test -Dtest=RetrievalModeResolverTest
 */
@ExtendWith(MockitoExtension.class)
class RetrievalModeResolverTest {

    @Mock private RagProperties ragProperties;
    @Mock private EmbeddingClient embeddingClient;

    @InjectMocks private RetrievalModeResolver resolver;

    @Test
    void autoWithLocalProviderShouldUseSimpleWithoutNetwork() {
        when(ragProperties.getMode()).thenReturn("auto");
        when(embeddingClient.isLocalProvider()).thenReturn(true);
        when(embeddingClient.describe()).thenReturn("本地模型(512 维)");

        assertEquals(RetrievalModeResolver.MODE_SIMPLE, resolver.detect());
        System.out.println("本地向量 → " + resolver.getReason());
        assertTrue(resolver.getReason().contains("本地模型"));
    }

    @Test
    void autoWithRemoteProviderAndNoKeyShouldFallbackToFramework() {
        when(ragProperties.getMode()).thenReturn("auto");
        when(embeddingClient.isLocalProvider()).thenReturn(false);
        when(embeddingClient.isApiKeyConfigured()).thenReturn(false);

        assertEquals(RetrievalModeResolver.MODE_FRAMEWORK, resolver.detect());
        System.out.println("远程未配 Key → " + resolver.getReason());
        assertTrue(resolver.getReason().contains("api-key"));
    }

    @Test
    void autoWithWorkingRemoteEmbeddingShouldUseSimple() {
        when(ragProperties.getMode()).thenReturn("auto");
        when(embeddingClient.isLocalProvider()).thenReturn(false);
        when(embeddingClient.isApiKeyConfigured()).thenReturn(true);
        when(embeddingClient.embed(anyString())).thenReturn(new double[1024]);

        assertEquals(RetrievalModeResolver.MODE_SIMPLE, resolver.detect());
        System.out.println("远程可用 → " + resolver.getReason());
        assertTrue(resolver.isSimple());
    }

    @Test
    void autoWithUnreachableRemoteEmbeddingShouldFallbackToFramework() {
        when(ragProperties.getMode()).thenReturn("auto");
        when(embeddingClient.isLocalProvider()).thenReturn(false);
        when(embeddingClient.isApiKeyConfigured()).thenReturn(true);
        when(embeddingClient.embed(anyString()))
                .thenThrow(new RuntimeException("Connection timed out"));

        assertEquals(RetrievalModeResolver.MODE_FRAMEWORK, resolver.detect());
        System.out.println("外网不通 → " + resolver.getReason());
        assertTrue(resolver.getReason().contains("回落 framework"));
    }

    @Test
    void explicitModeShouldOverrideProbe() {
        when(ragProperties.getMode()).thenReturn("simple");
        assertEquals(RetrievalModeResolver.MODE_SIMPLE, resolver.detect());
        System.out.println("强制 simple → " + resolver.getReason());

        resolver.refresh();
        when(ragProperties.getMode()).thenReturn("framework");
        assertEquals(RetrievalModeResolver.MODE_FRAMEWORK, resolver.detect());
        System.out.println("强制 framework → " + resolver.getReason());
    }
}
