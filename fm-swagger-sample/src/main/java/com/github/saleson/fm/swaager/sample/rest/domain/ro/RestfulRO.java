package com.github.saleson.fm.swaager.sample.rest.domain.ro;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
@ApiModel(description = "restful 测试FO")
public class RestfulRO {

  @ApiModelProperty(value = "http请求方法")
  private String httpMethod;

  @ApiModelProperty(value = "资源名称", example = "资源1")
  private String name;

  @ApiModelProperty(
      value = "资源项",
      required = true,
      allowableValues = "item1,item2,item3",
      allowEmptyValue = true,
      example = "item1")
  private String item;
}
