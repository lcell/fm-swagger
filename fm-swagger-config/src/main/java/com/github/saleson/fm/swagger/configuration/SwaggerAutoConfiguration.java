package com.github.saleson.fm.swagger.configuration;

import com.github.saleson.fm.swagger.configuration.properties.SwaggerProperties;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import springfox.documentation.builders.*;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.Defaults;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.ApiSelectorBuilder;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.ApiKeyVehicle;
import springfox.documentation.swagger.web.UiConfiguration;
import springfox.documentation.swagger.web.UiConfigurationBuilder;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;

@Configuration
@ConditionalOnProperty(name = "swagger.enabled")
@Import({Swagger2Configuration.class})
public class SwaggerAutoConfiguration implements BeanFactoryAware {

    private BeanFactory beanFactory;

    @Bean
    @ConditionalOnMissingBean
    public SwaggerProperties swaggerProperties() {
        return new SwaggerProperties();
    }

    @Bean
    public UiConfiguration uiConfiguration(SwaggerProperties swaggerProperties) {
        return UiConfigurationBuilder.builder()
                .deepLinking(swaggerProperties.getUiConfig().getDeepLinking())
                .defaultModelExpandDepth(swaggerProperties.getUiConfig().getDefaultModelExpandDepth())
                .defaultModelRendering(swaggerProperties.getUiConfig().getDefaultModelRendering())
                .defaultModelsExpandDepth(swaggerProperties.getUiConfig().getDefaultModelsExpandDepth())
                .displayOperationId(swaggerProperties.getUiConfig().getDisplayOperationId())
                .displayRequestDuration(swaggerProperties.getUiConfig().getDisplayRequestDuration())
                .docExpansion(swaggerProperties.getUiConfig().getDocExpansion())
                .maxDisplayedTags(swaggerProperties.getUiConfig().getMaxDisplayedTags())
                .operationsSorter(swaggerProperties.getUiConfig().getOperationsSorter())
                .showExtensions(swaggerProperties.getUiConfig().getShowExtensions())
                .tagsSorter(swaggerProperties.getUiConfig().getTagsSorter())
                .validatorUrl(swaggerProperties.getUiConfig().getValidatorUrl())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(UiConfiguration.class)
    @ConditionalOnProperty(name = "swagger.enabled")
    public List<Docket> createRestApi(SwaggerProperties swaggerProperties) {
        // 没有分组
        if (swaggerProperties.getDocket().size() == 0) {
            buildDocketNotGroup(swaggerProperties);
        }
        return buildDocketsGroup(swaggerProperties);
    }


    /**
     * 没有分组时创建dockets
     *
     * @param swaggerProperties swagger配置
     * @return 创建的dockets
     */
    private List<Docket> buildDocketNotGroup(SwaggerProperties swaggerProperties) {
        List<Docket> docketList = new LinkedList<>();
        ConfigurableBeanFactory configurableBeanFactory = (ConfigurableBeanFactory) beanFactory;
        // 没有分组
        ApiInfo apiInfo = buildApiInfo(swaggerProperties.getApiInfo());

        Docket docketForBuilder =
                new Docket(DocumentationType.SWAGGER_2)
                        .host(swaggerProperties.getHost())
                        .apiInfo(apiInfo)
                        .securityContexts(Collections.singletonList(securityContext()))
                        .globalOperationParameters(
                                buildGlobalOperationParametersFromSwaggerProperties(
                                        swaggerProperties.getGlobalOperationParameters()));

        Docket docket = fillAllAndBuildDocket(
                docketForBuilder, swaggerProperties.getDocketSelect(), swaggerProperties.getIgnoredParameterTypes());
        configurableBeanFactory.registerSingleton("defaultDocket", docket);
        docketList.add(docket);
        return docketList;
    }


    /**
     * 分组创建 dockets
     *
     * @param swaggerProperties swagger配置
     * @return 创建的dockets
     */
    private List<Docket> buildDocketsGroup(SwaggerProperties swaggerProperties) {
        List<Docket> docketList = new LinkedList<>();
        ConfigurableBeanFactory configurableBeanFactory = (ConfigurableBeanFactory) beanFactory;
        // 分组创建
        for (String groupName : swaggerProperties.getDocket().keySet()) {
            SwaggerProperties.DocketInfo docketInfo = swaggerProperties.getDocket().get(groupName);

            ApiInfo apiInfo = buildApiInfo(docketInfo.getApiInfo());

            Docket docketForBuilder =
                    new Docket(DocumentationType.SWAGGER_2)
                            .host(swaggerProperties.getHost())
                            .apiInfo(apiInfo)
                            .securityContexts(Collections.singletonList(securityContext()))
                            .globalOperationParameters(
                                    assemblyGlobalOperationParameters(
                                            swaggerProperties.getGlobalOperationParameters(),
                                            docketInfo.getGlobalOperationParameters()))
                            .groupName(groupName);

            Docket docket = fillAllAndBuildDocket(
                    docketForBuilder, docketInfo.getDocketSelect(), docketInfo.getIgnoredParameterTypes());
            configurableBeanFactory.registerSingleton(groupName, docket);
            docketList.add(docket);
        }
        return docketList;
    }


    /**
     * 补全并创建Docket
     *
     * @param docketForBuilder      docketBuilder
     * @param docketSelect          docket 选择信息
     * @param ignoredParameterTypes 忽略的参数类型列表
     * @return 创建的docket
     */
    private Docket fillAllAndBuildDocket(Docket docketForBuilder, SwaggerProperties.DocketSelect docketSelect, List<Class<?>> ignoredParameterTypes) {
        setSecuritySchemes(docketForBuilder);
        setGlobalResponseMessage(docketForBuilder);


        ApiSelectorBuilder apiSelectorBuilder = docketForBuilder.select();
        docketSelect(docketSelect, apiSelectorBuilder);
        Docket docket = apiSelectorBuilder.build();

        /* ignoredParameterTypes **/
        Class<?>[] array = new Class[ignoredParameterTypes.size()];
        Class<?>[] ignoredParameterTypeAry = ignoredParameterTypes.toArray(array);
        docket.ignoredParameterTypes(ignoredParameterTypeAry);
        return docket;
    }

    /**
     * 分析docketApi的选择规则， 并载入docket中
     *
     * @param docketSelect       docket 选择规则
     * @param apiSelectorBuilder apiSelectorBuilder
     */
    private void docketSelect(SwaggerProperties.DocketSelect docketSelect, ApiSelectorBuilder apiSelectorBuilder) {
        // base-path处理
        // 当没有配置任何path的时候，解析/**
        if (docketSelect.getBasePath().isEmpty()) {
            docketSelect.getBasePath().add("/**");
        }
        List<Predicate<String>> basePath = new ArrayList();
        for (String path : docketSelect.getBasePath()) {
            basePath.add(PathSelectors.ant(path));
        }

        // exclude-path处理
        List<Predicate<String>> excludePath = new ArrayList<>();
        for (String path : docketSelect.getExcludePath()) {
            excludePath.add(PathSelectors.ant(path));
        }

        apiSelectorBuilder
                .apis(RequestHandlerSelectors.basePackage(docketSelect.getBasePackage()))
                .paths(
                        Predicates.and(
                                Predicates.not(Predicates.or(excludePath)), Predicates.or(basePath)));

    }

    private ApiInfo buildApiInfo(SwaggerProperties.ApiInfo swaggerApiInfo) {
        SwaggerProperties swaggerProperties = swaggerProperties();
        if (swaggerApiInfo == swaggerProperties.getApiInfo()) {
            return new ApiInfoBuilder()
                    .title(swaggerApiInfo.getTitle())
                    .description(swaggerApiInfo.getDescription())
                    .version(swaggerApiInfo.getVersion())
                    .license(swaggerApiInfo.getLicense())
                    .licenseUrl(swaggerApiInfo.getLicenseUrl())
                    .contact(
                            new Contact(
                                    swaggerApiInfo.getContact().getName(),
                                    swaggerApiInfo.getContact().getUrl(),
                                    swaggerApiInfo.getContact().getEmail()))
                    .termsOfServiceUrl(swaggerApiInfo.getTermsOfServiceUrl())
                    .build();
        }
        SwaggerProperties.ApiInfo proApiInfo = swaggerProperties.getApiInfo();
        return new ApiInfoBuilder()
                .title(defaultString(swaggerApiInfo.getTitle(), proApiInfo.getTitle()))
                .description(defaultString(swaggerApiInfo.getDescription(), proApiInfo.getDescription()))
                .version(defaultString(swaggerApiInfo.getVersion(), proApiInfo.getVersion()))
                .license(defaultString(swaggerApiInfo.getLicense(), proApiInfo.getLicense()))
                .licenseUrl(defaultString(swaggerApiInfo.getLicenseUrl(), proApiInfo.getLicenseUrl()))
                .contact(
                        new Contact(
                                defaultString(swaggerApiInfo.getContact().getName(), proApiInfo.getContact().getName()),
                                defaultString(swaggerApiInfo.getContact().getUrl(), proApiInfo.getContact().getUrl()),
                                defaultString(swaggerApiInfo.getContact().getEmail(), proApiInfo.getContact().getEmail()))
                )
                .termsOfServiceUrl(defaultString(swaggerApiInfo.getTermsOfServiceUrl(), proApiInfo.getTermsOfServiceUrl()))
                .build();

    }

    private String defaultString(String str1, String str2) {
        return org.apache.commons.lang3.StringUtils.defaultIfEmpty(str1, str2);
    }

    /**
     * 配置基于 ApiKey 的鉴权对象
     *
     * @return 返回ApiKey 的鉴权对象
     */
    private ApiKey apiKey() {
        return new ApiKey(
                swaggerProperties().getAuthorization().getName(),
                swaggerProperties().getAuthorization().getKeyName(),
                ApiKeyVehicle.HEADER.getValue());
    }

    /**
     * 配置基于 BasicAuth 的鉴权对象
     *
     * @return 返回BasicAuth 的鉴权对象
     */
    private BasicAuth basicAuth() {
        return new BasicAuth(swaggerProperties().getAuthorization().getName());
    }

    /**
     * 配置默认的全局鉴权策略的开关，以及通过正则表达式进行匹配；默认 ^.*$ 匹配所有URL 其中 securityReferences 为配置启用的鉴权策略
     *
     * @return
     */
    private SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(defaultAuth())
                .forPaths(PathSelectors.regex(swaggerProperties().getAuthorization().getAuthRegex()))
                .build();
    }

    /**
     * 配置默认的全局鉴权策略；其中返回的 SecurityReference 中，reference 即为ApiKey对象里面的name，保持一致才能开启全局鉴权
     *
     * @return
     */
    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Collections.singletonList(
                SecurityReference.builder()
                        .reference(swaggerProperties().getAuthorization().getName())
                        .scopes(authorizationScopes)
                        .build());
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    private List<Parameter> buildGlobalOperationParametersFromSwaggerProperties(
            List<SwaggerProperties.GlobalOperationParameter> globalOperationParameters) {
        List<Parameter> parameters = newArrayList();

        if (Objects.isNull(globalOperationParameters)) {
            return parameters;
        }
        for (SwaggerProperties.GlobalOperationParameter globalOperationParameter :
                globalOperationParameters) {
            parameters.add(
                    new ParameterBuilder()
                            .name(globalOperationParameter.getName())
                            .description(globalOperationParameter.getDescription())
                            .modelRef(new ModelRef(globalOperationParameter.getModelRef()))
                            .parameterType(globalOperationParameter.getParameterType())
                            .required(globalOperationParameter.isRequired())
                            .defaultValue(globalOperationParameter.getDefaultValue())
                            .build());
        }
        return parameters;
    }

    /**
     * 局部参数按照name覆盖局部参数
     *
     * @param globalOperationParameters 全局参数配置
     * @param docketOperationParameters docket全局参数配置
     * @return 返回全局的操作参数
     */
    private List<Parameter> assemblyGlobalOperationParameters(
            List<SwaggerProperties.GlobalOperationParameter> globalOperationParameters,
            List<SwaggerProperties.GlobalOperationParameter> docketOperationParameters) {

        if (Objects.isNull(docketOperationParameters) || docketOperationParameters.isEmpty()) {
            return buildGlobalOperationParametersFromSwaggerProperties(globalOperationParameters);
        }

        Set<String> docketNames =
                docketOperationParameters.stream()
                        .map(SwaggerProperties.GlobalOperationParameter::getName)
                        .collect(Collectors.toSet());

        List<SwaggerProperties.GlobalOperationParameter> resultOperationParameters = newArrayList();

        if (Objects.nonNull(globalOperationParameters)) {
            for (SwaggerProperties.GlobalOperationParameter parameter : globalOperationParameters) {
                if (!docketNames.contains(parameter.getName())) {
                    resultOperationParameters.add(parameter);
                }
            }
        }

        resultOperationParameters.addAll(docketOperationParameters);
        return buildGlobalOperationParametersFromSwaggerProperties(resultOperationParameters);
    }

    /**
     * 设置全局响应消息
     *
     * @param swaggerProperties swaggerProperties 支持 POST,GET,PUT,PATCH,DELETE,HEAD,OPTIONS,TRACE
     * @param docketForBuilder  swagger docket builder
     */
    private void buildGlobalResponseMessage(
            SwaggerProperties swaggerProperties, Docket docketForBuilder) {

        SwaggerProperties.GlobalResponseMessage globalResponseMessages =
                swaggerProperties.getGlobalResponseMessage();

        Map<RequestMethod, List<ResponseMessage>> defaultResMsgs = new HashMap<>();
        if (swaggerProperties.isApplyDefaultResponseMessages()) {
            Defaults defaults = new Defaults();
            defaultResMsgs = defaults.defaultResponseMessages();
        }

        /* POST,GET,PUT,PATCH,DELETE,HEAD,OPTIONS,TRACE 响应消息体 **/
        List<ResponseMessage> allResponseMessages =
                getResponseMessageList(globalResponseMessages.getAll());
        List<ResponseMessage> postResponseMessages =
                getResponseMessageList(globalResponseMessages.getPost());
        List<ResponseMessage> getResponseMessages =
                getResponseMessageList(globalResponseMessages.getGet());
        List<ResponseMessage> putResponseMessages =
                getResponseMessageList(globalResponseMessages.getPut());
        List<ResponseMessage> patchResponseMessages =
                getResponseMessageList(globalResponseMessages.getPatch());
        List<ResponseMessage> deleteResponseMessages =
                getResponseMessageList(globalResponseMessages.getDelete());
        List<ResponseMessage> headResponseMessages =
                getResponseMessageList(globalResponseMessages.getHead());
        List<ResponseMessage> optionsResponseMessages =
                getResponseMessageList(globalResponseMessages.getOptions());
        List<ResponseMessage> trackResponseMessages =
                getResponseMessageList(globalResponseMessages.getTrace());

        docketForBuilder
                .useDefaultResponseMessages(swaggerProperties.isApplyDefaultResponseMessages())
                .globalResponseMessage(
                        RequestMethod.POST,
                        unionAll(
                                defaultResMsgs.get(RequestMethod.POST), allResponseMessages, postResponseMessages))
                .globalResponseMessage(
                        RequestMethod.GET,
                        unionAll(
                                defaultResMsgs.get(RequestMethod.GET), allResponseMessages, getResponseMessages))
                .globalResponseMessage(
                        RequestMethod.PUT,
                        unionAll(
                                defaultResMsgs.get(RequestMethod.PUT), allResponseMessages, putResponseMessages))
                .globalResponseMessage(
                        RequestMethod.PATCH,
                        unionAll(
                                defaultResMsgs.get(RequestMethod.PATCH), allResponseMessages, patchResponseMessages))
                .globalResponseMessage(
                        RequestMethod.DELETE,
                        unionAll(
                                defaultResMsgs.get(RequestMethod.DELETE), allResponseMessages, deleteResponseMessages))
                .globalResponseMessage(
                        RequestMethod.HEAD,
                        unionAll(
                                defaultResMsgs.get(RequestMethod.HEAD), allResponseMessages, headResponseMessages))
                .globalResponseMessage(
                        RequestMethod.OPTIONS,
                        unionAll(
                                defaultResMsgs.get(RequestMethod.OPTIONS), allResponseMessages, optionsResponseMessages))
                .globalResponseMessage(
                        RequestMethod.TRACE,
                        unionAll(
                                defaultResMsgs.get(RequestMethod.TRACE), allResponseMessages, trackResponseMessages));
    }

    /**
     * 获取返回消息体列表
     *
     * @param globalResponseMessageBodyList 全局Code消息返回集合
     * @return
     */
    private List<ResponseMessage> getResponseMessageList(
            List<SwaggerProperties.GlobalResponseMessageBody> globalResponseMessageBodyList) {
        List<ResponseMessage> responseMessages = new ArrayList<>();
        for (SwaggerProperties.GlobalResponseMessageBody globalResponseMessageBody :
                globalResponseMessageBodyList) {
            ResponseMessageBuilder responseMessageBuilder = new ResponseMessageBuilder();
            responseMessageBuilder
                    .code(globalResponseMessageBody.getCode())
                    .message(globalResponseMessageBody.getMessage());

            if (!StringUtils.isEmpty(globalResponseMessageBody.getModelRef())) {
                responseMessageBuilder.responseModel(new ModelRef(globalResponseMessageBody.getModelRef()));
            }
            responseMessages.add(responseMessageBuilder.build());
        }

        return responseMessages;
    }

    private void setSecuritySchemes(Docket docketForBuilder) {
        SwaggerProperties swaggerProperties = swaggerProperties();
        if ("BasicAuth".equalsIgnoreCase(swaggerProperties.getAuthorization().getType())) {
            docketForBuilder.securitySchemes(Collections.singletonList(basicAuth()));
        } else if (!"None".equalsIgnoreCase(swaggerProperties.getAuthorization().getType())) {
            docketForBuilder.securitySchemes(Collections.singletonList(apiKey()));
        }
    }

    private void setGlobalResponseMessage(Docket docketForBuilder) {
        SwaggerProperties swaggerProperties = swaggerProperties();
        // 全局响应消息
        //        if (!swaggerProperties.getApplyDefaultResponseMessages()) {
        buildGlobalResponseMessage(swaggerProperties, docketForBuilder);
        //        }
    }

    private <T> List<T> unionAll(List<T>... lists) {
        List<T> list = new ArrayList<>();
        for (List<T> item : lists) {
            if (!Objects.isNull(item)) {
                list.addAll(item);
            }
        }
        return list;
    }
}
