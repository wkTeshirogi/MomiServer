package jp.xoki.web.zunda.smarthomewebserverbackend;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/** SmartHomeWebServerBackendのSpring Boot起動クラス。 */
@OpenAPIDefinition(info = @Info(
        title = "受講生管理システム",
        description = "受講生情報を管理するためのシステムです。主に受講生の個人情報と受講しているコース情報の管理、操作を行います"

))
@SpringBootApplication
public class SmartHomeWebServerBackendApplication {

    /**
     * Spring Bootアプリケーションを起動する。
     *
     * @param args 起動引数
     */
    public static void main(String[] args) {
        SpringApplication.run(SmartHomeWebServerBackendApplication.class, args);
    }

}
