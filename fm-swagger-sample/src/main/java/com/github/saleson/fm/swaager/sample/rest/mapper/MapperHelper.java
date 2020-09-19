package com.github.saleson.fm.swaager.sample.rest.mapper;

import com.github.saleson.fm.swaager.sample.rest.domain.ro.RestfulRO;
import com.github.saleson.fm.swaager.sample.rest.domain.vo.RestfulVO;

/**
 * @author saleson
 * @date 2020-09-19 11:18
 */
public class MapperHelper {

    public static RestfulVO ro2vo(RestfulRO ro){
        RestfulVO vo = new RestfulVO();
        vo.setHttpMethod(ro.getHttpMethod());
        vo.setId(0l);
        vo.setName(ro.getName());
        return vo;
    }
}
