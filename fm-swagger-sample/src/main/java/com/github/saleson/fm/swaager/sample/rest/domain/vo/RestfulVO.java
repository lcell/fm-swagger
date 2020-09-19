package com.github.saleson.fm.swaager.sample.rest.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@ApiModel(description = "restful 测试VO")
public class RestfulVO {
    @ApiModelProperty(value = "资源ID")
    private Long id;
    @ApiModelProperty(value = "http请求方法")
    private String httpMethod;
    @ApiModelProperty(value = "资源名称")
    private String name;
}
