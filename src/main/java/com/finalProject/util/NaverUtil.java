package com.finalProject.util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletResponse;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.finalProject.model.MemberDTO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Component // Spring 컨테이너가 해당 클래스를 빈으로 등록
public class NaverUtil {

	private String ClientID;
	private String Client_Secret;
	private String getCodeUrl;
	private String getTokenUrl;
	private String redirectUri;
	private String userInfoRequestUrl;
	private String dummyState;
	
	private final RestTemplate restTemplate = new RestTemplate();
	private final Gson gson = new Gson();
	
	public NaverUtil() {
		try {
			readProperties();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// 코드 받기
	public void getCode(HttpServletResponse response) throws FileNotFoundException, IOException {
		readProperties();
		String url = getCodeUrl + "?client_id=" + ClientID + "&redirect_uri=" + redirectUri + "&response_type=code" + "&state=" + dummyState;
		System.out.println(url + "로 리다이렉트함");
		System.out.println("다음 목적지 : " + this.redirectUri);
		response.sendRedirect(url);
	}

	// 토큰 받기
	public String getAccessToken(String code) {
		String accessToken = "";
		String requestUrl = this.getTokenUrl + 
				"?grant_type=authorization_code" + 
				"&client_id=" + this.ClientID +
				"&client_secret=" + this.Client_Secret + 
		        "&code=" + code +
		        "&state=" + this.dummyState;

		ResponseEntity<Map> response = restTemplate.getForEntity(requestUrl, Map.class);
		Map<String, Object> body = response.getBody();
		
		for (String key : body.keySet()) {
			System.out.println("key : " + key + ". value : " + body.get(key).toString());
		}
		accessToken = body.get("access_token").toString();
		return accessToken;
	}

	// 유저 정보 받기
	public MemberDTO getUserInfo(String accessToken) {
		MemberDTO userInfo = new MemberDTO();
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(headers);
		
		ResponseEntity<Map> response = this.restTemplate.postForEntity(this.userInfoRequestUrl, entity, Map.class);
		Map<String, Object> body = response.getBody();
		
		for (String key : body.keySet()) {
			System.out.println("key : " + key + ". value : " + body.get(key).toString());
		}
		
		JsonObject innerResponse = gson.fromJson(body.get("response").toString(), JsonObject.class);
		userInfo.setNaver_id(innerResponse.get("id").getAsString());
		userInfo.setEmail(innerResponse.get("email").getAsString());
		userInfo.setMember_name(innerResponse.get("name").getAsString());

		// 사용자 정보를 담고 있는 DTO 반환
		return userInfo;
	}

	// 응답을 읽어 문자열로 반환하는 메서드
	private String readResponse(BufferedReader br) throws IOException {
		String line;
		StringBuilder responseSb = new StringBuilder();
		while ((line = br.readLine()) != null) {
			responseSb.append(line);
		}
		return responseSb.toString();
	}

	// naver.properties파일 읽기
	private void readProperties() throws FileNotFoundException, IOException {
		Properties prop = new Properties();
		// 해당 경로의 properties를 받음.
		// 클래스패스에서 properties 파일을 읽음 src/main/resources
		InputStream input = getClass().getClassLoader().getResourceAsStream("naver.properties");
		if (input == null) {
			System.out.println("naver.properties를 찾을수 없습니다.");
		}
		prop.load(input);
		// 필요한 값 저장
		this.ClientID = prop.get("ClientID") + "";
		this.Client_Secret = prop.get("Client_Secret") + "";
		this.getCodeUrl = prop.get("getCodeUrl") + "";
		this.getTokenUrl = prop.get("getTokenUrl") + "";
		this.redirectUri = prop.get("redirectUri") + "";
		this.userInfoRequestUrl = prop.getProperty("userInfoRequestUrl") + "";
		this.dummyState = prop.get("dummyState") + "";
	}
}
