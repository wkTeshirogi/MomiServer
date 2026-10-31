package jp.xoki.web.zunda.smarthomewebserverbackend.common;

import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionParameterRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchDefinitionRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchExecutionHistoryRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.batch.repository.BatchScheduleRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.DocumentTextExtractionService;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.config.GatewayRateLimiter;
import jp.xoki.web.zunda.smarthomewebserverbackend.common.document.TesseractOcrEngine;
import jp.xoki.web.zunda.smarthomewebserverbackend.status.websocket.StatusWebSocketHandler;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleImportRepository;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleImportRequestSizeFilter;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleImportService;
import jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports.VehicleImportStorage;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class SpotBugsFilterPolicyTest {

	@Test
	void exclusionsOnlyCoverReviewedConstructorsAndValidatedOcrLaunch() throws Exception {
		Set<String> reviewed = Set.of(
				target(GatewayRateLimiter.class, "gatewayProperties"),
				target(BatchDefinitionParameterRepository.class, "dsl"),
				target(BatchDefinitionRepository.class, "dsl"),
				target(BatchExecutionHistoryRepository.class, "dsl"),
				target(BatchScheduleRepository.class, "dsl"),
				target(DocumentTextExtractionService.class, "properties"),
				target(TesseractOcrEngine.class, "properties"),
				target(StatusWebSocketHandler.class, "sessions"),
				target(VehicleImportRepository.class, "dsl"),
				target(VehicleImportRequestSizeFilter.class, "properties"),
				target(VehicleImportService.class, "extractionService"),
				target(VehicleImportService.class, "properties"),
				target(VehicleImportService.class, "repository"),
				target(VehicleImportService.class, "storage"),
				target(VehicleImportStorage.class, "properties"));
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		Document document = factory.newDocumentBuilder().parse(Path.of("config/spotbugs/exclude.xml").toFile());
		Element root = document.getDocumentElement();
		assertThat(root.getTagName()).isEqualTo("FindBugsFilter");
		Set<String> actual = new HashSet<>();
		int ocrLaunchExclusions = 0;
		for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element match) {
				assertThat(match.getTagName()).isEqualTo("Match");
				assertThat(match.getAttributes().getLength()).isZero();
				if ("COMMAND_INJECTION".equals(only(match, "Bug").getAttribute("pattern"))) {
					assertValidatedOcrLaunch(match);
					ocrLaunchExclusions++;
					continue;
				}
				assertThat(match.getElementsByTagName("*").getLength()).isEqualTo(4);
				assertAttribute(match, "Bug", "pattern", "EI_EXPOSE_REP2");
				assertAttribute(match, "Method", "name", "<init>");
				Element classElement = only(match, "Class");
				assertThat(classElement.getAttributes().getLength()).isEqualTo(1);
				Class<?> owner = Class.forName(classElement.getAttribute("name"));
				Element fieldElement = only(match, "Field");
				assertThat(fieldElement.getAttributes().getLength()).isEqualTo(2);
				Field field = owner.getDeclaredField(fieldElement.getAttribute("name"));
				assertThat(fieldElement.getAttribute("type")).isEqualTo(field.getType().getName());
				assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
				assertThat(actual.add(target(owner, field.getName()))).isTrue();
			}
		}
		assertThat(actual).containsExactlyInAnyOrderElementsOf(reviewed);
		assertThat(ocrLaunchExclusions).isEqualTo(1);
	}

	private void assertValidatedOcrLaunch(Element match) throws ReflectiveOperationException {
		assertThat(match.getElementsByTagName("*").getLength()).isEqualTo(3);
		assertAttribute(match, "Bug", "pattern", "COMMAND_INJECTION");
		assertAttribute(match, "Class", "name", TesseractOcrEngine.class.getName());
		Element method = only(match, "Method");
		assertThat(method.getAttributes().getLength()).isEqualTo(3);
		assertThat(method.getAttribute("name")).isEqualTo("startTesseract");
		assertThat(method.getAttribute("params")).isEqualTo(Path.class.getName());
		assertThat(method.getAttribute("returns")).isEqualTo(Process.class.getName());
		Method launcher = TesseractOcrEngine.class.getDeclaredMethod("startTesseract", Path.class);
		assertThat(Modifier.isPrivate(launcher.getModifiers())).isTrue();
		assertThat(launcher.getReturnType()).isEqualTo(Process.class);
	}

	private void assertAttribute(Element match, String tag, String attribute, String expected) {
		Element element = only(match, tag);
		assertThat(element.getAttributes().getLength()).isEqualTo(1);
		assertThat(element.getAttribute(attribute)).isEqualTo(expected);
	}

	private Element only(Element parent, String tag) {
		NodeList nodes = parent.getElementsByTagName(tag);
		assertThat(nodes.getLength()).isEqualTo(1);
		return (Element) nodes.item(0);
	}

	private String target(Class<?> owner, String field) {
		return owner.getName() + "#" + field;
	}
}
