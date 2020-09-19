package com.github.saleson.fm.swaager.sample.rest;

import com.github.saleson.fm.swaager.sample.rest.domain.ro.RestfulRO;
import com.github.saleson.fm.swaager.sample.rest.domain.vo.RestfulVO;
import com.github.saleson.fm.swaager.sample.rest.mapper.MapperHelper;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/restful")
public class RestfulResource {


  @ApiImplicitParams({@ApiImplicitParam(name = "id", value = "restful ID")})
  @GetMapping("/")
  public RestfulVO get(@RequestParam("id") Long id) {
    RestfulVO vo = new RestfulVO();
    vo.setId(id);
    vo.setHttpMethod("GET");
    vo.setName("Restful Get");
    return vo;
  }

  @ApiResponses(
      value = {
        @ApiResponse(
            code = 403,
            message = "没有操作权限"),
      })
  @ApiImplicitParam(name = "Content-Type", value = "请求内容的类型和编码", example = "application/json",paramType = "header")
  @PostMapping("/newRestful")
  public ResponseEntity<RestfulVO> post(@Validated @RequestBody RestfulRO fo) {
    RestfulVO vo = MapperHelper.ro2vo(fo);
    vo.setId(0L);
    return ResponseEntity.ok(vo);
  }

//  @GetMapping("/")
//  public ResponseEntity<RestfulVO> getFO(@Validated RestfulRO fo) {
//    RestfulVO vo = restfulMapper.fo2vo(fo);
//    vo.setId(0L);
//    return ResponseEntity.ok(vo);
//  }

  @ApiImplicitParams({@ApiImplicitParam(name = "id", value = "restful ID", required = true, paramType = "query")})
  @PutMapping("/editRestful")
  public ResponseEntity<RestfulVO> edit(@RequestParam("id") Long id, @RequestBody RestfulRO fo){
    RestfulVO vo = MapperHelper.ro2vo(fo);
    vo.setId(id);
    return ResponseEntity.ok(vo);
  }

  @ApiImplicitParams({@ApiImplicitParam(name = "id", value = "restful ID", required = true, paramType = "query")})
  @PatchMapping("/modifyRestful")
  public ResponseEntity<RestfulVO> modify(@RequestParam("id") Long id, @RequestBody RestfulRO fo){
    RestfulVO vo = MapperHelper.ro2vo(fo);
    vo.setId(id);
    return ResponseEntity.ok(vo);
  }

  @ApiImplicitParams({@ApiImplicitParam(name = "id", value = "restful ID", required = true, paramType = "query")})
  @DeleteMapping("/delRestful")
  public ResponseEntity<Void> delete(@RequestParam("id") Long id){
    return ResponseEntity.ok().build();
  }
}
