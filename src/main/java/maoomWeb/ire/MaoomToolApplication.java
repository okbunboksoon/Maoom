package maoomWeb.ire;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("maoomWeb.ire.user.mapper")
@SpringBootApplication(scanBasePackages = "maoomWeb.ire")
/**
 * MAOOM 웹 애플리케이션의 실행 진입점.
 * Spring 컴포넌트와 MyBatis 매퍼를 검색하여 애플리케이션을 시작한다.
 */
public class MaoomToolApplication {

	public static void main(String[] args) {
		SpringApplication.run(MaoomToolApplication.class, args);
	}
}
