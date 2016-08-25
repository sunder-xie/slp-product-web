package com.ai.slp.product.web.controller.normproduct;

import com.ai.opt.base.vo.BaseListResponse;
import com.ai.opt.base.vo.PageInfoResponse;
import com.ai.opt.sdk.dubbo.util.DubboConsumerFactory;
import com.ai.opt.sdk.web.model.ResponseData;
import com.ai.platform.common.api.sysuser.interfaces.ISysUserQuerySV;
import com.ai.platform.common.api.sysuser.param.SysUserQueryRequest;
import com.ai.platform.common.api.sysuser.param.SysUserQueryResponse;
import com.ai.slp.common.api.cache.interfaces.ICacheSV;
import com.ai.slp.common.api.cache.param.SysParam;
import com.ai.slp.common.api.cache.param.SysParamMultiCond;
import com.ai.slp.common.api.cache.param.SysParamSingleCond;
import com.ai.slp.product.api.normproduct.interfaces.INormProductSV;
import com.ai.slp.product.api.normproduct.param.*;
import com.ai.slp.product.api.productcat.param.ProdCatInfo;
import com.ai.slp.product.api.storage.interfaces.IStorageSV;
import com.ai.slp.product.api.storage.param.StorageGroupQuery;
import com.ai.slp.product.api.storage.param.StorageGroupRes;
import com.ai.slp.product.api.storage.param.StorageRes;
import com.ai.slp.product.web.constants.ComCacheConstants;
import com.ai.slp.product.web.constants.ProductCatConstants;
import com.ai.slp.product.web.constants.SysCommonConstants;
import com.ai.slp.product.web.service.AttrAndValService;
import com.ai.slp.product.web.service.ProdCatService;
import com.ai.slp.product.web.util.AdminUtil;
import com.ai.slp.product.web.util.DateUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 标准品查询
 * @author jiawen
 */
@Controller
@RequestMapping("/normprodquery")
public class NormProdQueryController {
	private static final Logger LOG = LoggerFactory.getLogger(NormProdQueryController.class);

	@Autowired
	private ProdCatService prodCatService;
	@Autowired
    private AttrAndValService attrAndValService;
	
	/**
	 * 进入页面调用-加载类目
	 */
	@RequestMapping("/list")
	public String editQuery(Model uiModel) {
		Map<Short, List<ProdCatInfo>> productCatMap = prodCatService.loadCat();
		uiModel.addAttribute("count", productCatMap.size() - 1);
		uiModel.addAttribute("catInfoMap", productCatMap);
		return "normproduct/normproductlist";
	}
	
	/**
	 * 点击查询按钮调用方法-查询标准品
	 * @return
	 */
	@RequestMapping("/getNormProductList")
	@ResponseBody
	private ResponseData<PageInfoResponse<NormProdResponse>> queryNormProduct(HttpServletRequest request,NormProdRequest productRequest){
		ResponseData<PageInfoResponse<NormProdResponse>> responseData = null;
		try {
			//查询条件
			queryBuilder(request, productRequest);
			
			PageInfoResponse<NormProdResponse> result = queryProductByState(productRequest);
			
			responseData = new ResponseData<PageInfoResponse<NormProdResponse>>(ResponseData.AJAX_STATUS_SUCCESS, "查询成功",
					result);
		} catch (Exception e) {
			responseData = new ResponseData<PageInfoResponse<NormProdResponse>>(ResponseData.AJAX_STATUS_FAILURE,
					"获取信息异常");
			LOG.error("获取信息出错：", e);
		}
		return responseData;
	}
	
	/**
	 * 根据状态不同查询商品
	 * 
	 * @param 
	 * @return
	 */
	private PageInfoResponse<NormProdResponse> queryProductByState(NormProdRequest productRequest) {
		INormProductSV normProductSV = DubboConsumerFactory.getService(INormProductSV.class);
		PageInfoResponse<NormProdResponse> result = normProductSV.queryNormProduct(productRequest);
		ICacheSV cacheSV = DubboConsumerFactory.getService("iCacheSV");
		ISysUserQuerySV sysUserQuerySV = DubboConsumerFactory.getService(ISysUserQuerySV.class);
		SysParamSingleCond sysParamSingleCond = null;
		for (NormProdResponse normProdResponse : result.getResult()) {
			// 获取类型和状态
			if (StringUtils.isNotBlank(normProdResponse.getProductType())) {
				// 获取类型
				String productType = normProdResponse.getProductType();
				sysParamSingleCond = new SysParamSingleCond(AdminUtil.getTenantId(),
						ComCacheConstants.TypeProduct.CODE, ComCacheConstants.TypeProduct.PROD_PRODUCT_TYPE,
						productType);
				String productTypeName = cacheSV.getSysParamSingle(sysParamSingleCond).getColumnDesc();
				normProdResponse.setProductType(productTypeName);
				// 获取状态
				String state = normProdResponse.getState();
				sysParamSingleCond = new SysParamSingleCond(AdminUtil.getTenantId(),
						ComCacheConstants.NormProduct.CODE, ComCacheConstants.NormProduct.STATUS, state);
				String stateName = cacheSV.getSysParamSingle(sysParamSingleCond).getColumnDesc();
				normProdResponse.setState(stateName);
				
				//设置人员名称
				SysUserQueryRequest userQuery = new SysUserQueryRequest();
	            userQuery.setTenantId(AdminUtil.getTenantId());
	            Long createId = normProdResponse.getCreateId();
	            //设置创建者名称
	            if(createId != null){
	            	userQuery.setId(Long.toString(createId));
	            	SysUserQueryResponse serInfo = sysUserQuerySV.queryUserInfo(userQuery);
	            	if(serInfo != null){
	            		normProdResponse.setCreateName(serInfo.getName());
	            	}
	            }
	            Long operId = normProdResponse.getOperId();
	            //设置操作者名称
	            if(operId != null){
	            	userQuery.setId(Long.toString(operId));
	            	SysUserQueryResponse serInfo = sysUserQuerySV.queryUserInfo(userQuery);
	            	if(serInfo != null){
	            		normProdResponse.setOperName(serInfo.getName());
	            	}
	            }
			}
			
		}
		return result;
	}
	
	/**
	 * 查询条件检查设置  
	 */
	private void queryBuilder(HttpServletRequest request,NormProdRequest productRequest) {
		productRequest.setSupplierId(SysCommonConstants.COMMON_SUPPLIER_ID);
		productRequest.setTenantId(AdminUtil.getTenantId());
		productRequest.setSupplierId(SysCommonConstants.COMMON_SUPPLIER_ID);
		if(!request.getParameter("productId").isEmpty())
			productRequest.setStandedProdId(request.getParameter("productId"));
		if(!request.getParameter("productName").isEmpty())
			productRequest.setStandedProductName(request.getParameter("productName"));
		
		
		if (StringUtils.isNotBlank(request.getParameter("operStartTimeStr"))) {
			String startTime = request.getParameter("operStartTimeStr")+" 00:00:00";
			productRequest.setOperStartTime(DateUtil.getTimestamp(startTime, "yyyy-MM-dd HH:mm:ss"));
		}
		
		if (StringUtils.isNotBlank(request.getParameter("operEndTimeStr"))) {
				String endTime = request.getParameter("operEndTimeStr")+" 23:59:59";
				productRequest.setOperEndTime(DateUtil.getTimestamp(endTime, "yyyy-MM-dd HH:mm:ss"));
			}
		
	}
	
	/**
     * 显示标准品库存编辑页面
     *
     * @param standedProdId
     * @return
     */
    @RequestMapping("/{id}")
    public String storageEdit(@PathVariable("id") String standedProdId, Model uiModel) {
        //标准品ID
        uiModel.addAttribute("standedProdId", standedProdId);
        //查询标准品信息
        NormProdUniqueReq normProdUniqueReq = new NormProdUniqueReq();
        normProdUniqueReq.setProductId(standedProdId);
        normProdUniqueReq.setTenantId(AdminUtil.getTenantId());
        normProdUniqueReq.setSupplierId(SysCommonConstants.COMMON_SUPPLIER_ID);
        INormProductSV normProductSV = DubboConsumerFactory.getService(INormProductSV.class);
        NormProdInfoResponse normProdInfoResponse = normProductSV.queryProducById(normProdUniqueReq);
        uiModel.addAttribute("normProdInfo", normProdInfoResponse);
        //查询类目链
        uiModel.addAttribute("catLinkList", prodCatService.queryLink(normProdInfoResponse.getProductCatId()));
        uiModel.addAttribute("productCatId", normProdInfoResponse.getProductCatId());
        //商品类型
        SysParamSingleCond paramSingleCond = new SysParamSingleCond();
        paramSingleCond.setTenantId(AdminUtil.getTenantId());
        paramSingleCond.setTypeCode(ComCacheConstants.TypeProduct.CODE);
        paramSingleCond.setParamCode(ComCacheConstants.TypeProduct.PROD_PRODUCT_TYPE);
        paramSingleCond.setColumnValue(normProdInfoResponse.getProductType());
        ICacheSV cacheSV = DubboConsumerFactory.getService(ICacheSV.class);
        SysParam sysParam = cacheSV.getSysParamSingle(paramSingleCond);
        uiModel.addAttribute("prodType", sysParam.getColumnDesc());
        //标准品关键属性
        AttrQuery attrQuery = new AttrQuery();
        attrQuery.setTenantId(AdminUtil.getTenantId());
        attrQuery.setProductId(normProdInfoResponse.getProductId());
        attrQuery.setAttrType(ProductCatConstants.ProductCatAttr.AttrType.ATTR_TYPE_KEY);
        AttrMap attrMap = normProductSV.queryAttrByNormProduct(attrQuery);
        uiModel.addAttribute("keyAttr", attrAndValService.getAttrAndVals(attrMap));
        //查询销售属性
        attrQuery.setAttrType(ProductCatConstants.ProductCatAttr.AttrType.ATTR_TYPE_SALE);
        attrMap = normProductSV.queryAttrByNormProduct(attrQuery);
        uiModel.addAttribute("saleAttr", attrAndValService.getAttrAndVals(attrMap));

        //查询库存组和库存信息
        StorageGroupQuery storageGroupQuery = new StorageGroupQuery();
        storageGroupQuery.setTenantId(AdminUtil.getTenantId());
        storageGroupQuery.setSupplierId(SysCommonConstants.COMMON_SUPPLIER_ID);
        storageGroupQuery.setProductId(normProdInfoResponse.getProductId());
        IStorageSV storageSV = DubboConsumerFactory.getService(IStorageSV.class);
        BaseListResponse<StorageGroupRes> storageGroupResList = storageSV.queryGroupInfoByNormProdId(storageGroupQuery);
        Map<String,SysParam> paramMap = getStorageStatus();
        for (StorageGroupRes storageGroupRes : storageGroupResList.getResult()) {
            // 获取库存组状态名
            String state = storageGroupRes.getState();
            paramSingleCond = new SysParamSingleCond(AdminUtil.getTenantId(),
                    ComCacheConstants.StateStorage.STORAGEGROUP_TYPR_CODE, ComCacheConstants.StateStorage.PARAM_CODE, state);
            String stateName = cacheSV.getSysParamSingle(paramSingleCond).getColumnDesc();
            storageGroupRes.setStateName(stateName);
            // 库存组优先级
            for (Short key : storageGroupRes.getStorageList().keySet()) {
                // 获取库存状态名
                for (StorageRes storageRes : storageGroupRes.getStorageList().get(key)) {
                    SysParam param = paramMap.get(storageRes.getState());
                    if (param!=null)
                    storageRes.setStateName(param.getColumnDesc());
                }
            }
        }
        uiModel.addAttribute("storGroupList", storageGroupResList.getResult());
        return "storage/storageEdit";
    }

    /**
     * 获取库存状态字典信息
     * @return
     */
    private Map<String,SysParam> getStorageStatus(){
        ICacheSV cacheSV = DubboConsumerFactory.getService(ICacheSV.class);
        SysParamMultiCond multiCond = new SysParamMultiCond(AdminUtil.getTenantId(),
                ComCacheConstants.StateStorage.STORAGE_TYPR_CODE, ComCacheConstants.StateStorage.PARAM_CODE);
        List<SysParam> sysParamList = cacheSV.getSysParamList(multiCond);
        Map<String,SysParam> paramMap = new HashMap<>();
        for (SysParam sysParam:sysParamList){
            paramMap.put(sysParam.getColumnValue(),sysParam);
        }
        return paramMap;
    }
	
}
