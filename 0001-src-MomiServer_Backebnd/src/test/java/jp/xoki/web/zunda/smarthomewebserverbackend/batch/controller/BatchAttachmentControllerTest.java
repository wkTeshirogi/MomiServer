package jp.xoki.web.zunda.smarthomewebserverbackend.batch.controller;

import java.io.IOException;
import java.util.UUID;
import jp.xoki.web.zunda.smarthomewebserverbackend.agent.service.AgentAuthorization;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.service.BatchAttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

class BatchAttachmentControllerTest {

	private final BatchAttachmentService files = mock(BatchAttachmentService.class);
	private final AgentAuthorization authorization = mock(AgentAuthorization.class);
	private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
	private final TransactionStatus transaction = mock(TransactionStatus.class);
	private BatchAttachmentController controller;

	@BeforeEach
	void setUp() {
		when(manager.getTransaction(any())).thenReturn(transaction);
		ProxyFactory proxy = new ProxyFactory(new BatchAttachmentController(files, authorization));
		TransactionInterceptor interceptor = new TransactionInterceptor();
		interceptor.setTransactionManager(manager);
		interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
		proxy.addAdvice(interceptor);
		controller = (BatchAttachmentController) proxy.getProxy();
	}

	@Test
	void downloadRollsBackWhenTheAttachmentCannotBeOpened() throws IOException {
		UUID executionId = UUID.randomUUID();
		when(files.download(executionId, 1L)).thenThrow(new IOException("unavailable"));

		assertThatIOException().isThrownBy(() -> controller.download(executionId, 1L));

		verify(authorization).requireExecution(executionId);
		verify(manager).rollback(transaction);
		verify(manager, never()).commit(any());
	}

	@Test
	void successfulDownloadCommitsAndRetainsTheResourceAndCacheHeader() throws IOException {
		UUID executionId = UUID.randomUUID();
		InputStreamResource resource = mock(InputStreamResource.class);
		when(files.download(executionId, 1L)).thenReturn(resource);

		ResponseEntity<Resource> response = controller.download(executionId, 1L);

		assertThat(response.getBody()).isSameAs(resource);
		assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
		verify(authorization).requireExecution(executionId);
		verify(manager).commit(transaction);
		verify(manager, never()).rollback(any());
	}
}
