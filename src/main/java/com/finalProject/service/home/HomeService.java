package com.finalProject.service.home;

import java.util.List;
import java.util.Map;

import com.finalProject.model.admin.homepage.BannerDTO;
import com.finalProject.model.home.HomeProductDTO;

public interface HomeService {
	
	// 홈페이지 데이터 가져오기
	Map<String, Object> getHomeData() throws Exception;

	List<HomeProductDTO> getNewProducts() throws Exception;

	List<BannerDTO> getBannerList() throws Exception;
}
