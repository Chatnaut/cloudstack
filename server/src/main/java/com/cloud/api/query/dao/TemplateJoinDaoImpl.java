// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.api.query.dao;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.response.ChildTemplateResponse;
import org.apache.cloudstack.api.response.TemplateResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.subsystem.api.storage.ObjectInDataStoreStateMachine;
import org.apache.cloudstack.engine.subsystem.api.storage.TemplateState;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.cloudstack.query.QueryService;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDao;
import org.apache.cloudstack.storage.datastore.db.ImageStoreVO;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.cloudstack.utils.security.DigestHelper;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.api.ApiDBUtils;
import com.cloud.api.ApiResponseHelper;
import com.cloud.api.query.vo.ResourceTagJoinVO;
import com.cloud.api.query.vo.TemplateJoinVO;
import com.cloud.deployasis.DeployAsIsConstants;
import com.cloud.deployasis.TemplateDeployAsIsDetailVO;
import com.cloud.deployasis.dao.TemplateDeployAsIsDetailsDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.storage.Storage;
import com.cloud.storage.Storage.TemplateType;
import com.cloud.storage.VMTemplateStorageResourceAssoc.Status;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VMTemplateDetailsDao;
import com.cloud.template.VirtualMachineTemplate;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.utils.Pair;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;


@Component
public class TemplateJoinDaoImpl extends GenericDaoBaseWithTagInformation<TemplateJoinVO, TemplateResponse> implements TemplateJoinDao {

    public static final Logger s_logger = Logger.getLogger(TemplateJoinDaoImpl.class);

    @Inject
    private ConfigurationDao  _configDao;
    @Inject
    private AccountService _accountService;
    @Inject
    private VMTemplateDao _vmTemplateDao;
    @Inject
    private TemplateDataStoreDao _templateStoreDao;
    @Inject
    private ImageStoreDao dataStoreDao;
    @Inject
    private VMTemplateDetailsDao _templateDetailsDao;
    @Inject
    private TemplateDeployAsIsDetailsDao templateDeployAsIsDetailsDao;

    private final SearchBuilder<TemplateJoinVO> tmpltIdPairSearch;

    private final SearchBuilder<TemplateJoinVO> tmpltIdSearch;

    private final SearchBuilder<TemplateJoinVO> tmpltIdsSearch;

    private final SearchBuilder<TemplateJoinVO> tmpltZoneSearch;

    private final SearchBuilder<TemplateJoinVO> activeTmpltSearch;

    protected TemplateJoinDaoImpl() {

        tmpltIdPairSearch = createSearchBuilder();
        tmpltIdPairSearch.and("templateState", tmpltIdPairSearch.entity().getTemplateState(), SearchCriteria.Op.IN);
        tmpltIdPairSearch.and("tempZonePairIN", tmpltIdPairSearch.entity().getTempZonePair(), SearchCriteria.Op.IN);
        tmpltIdPairSearch.done();

        tmpltIdSearch = createSearchBuilder();
        tmpltIdSearch.and("id", tmpltIdSearch.entity().getId(), SearchCriteria.Op.EQ);
        tmpltIdSearch.done();

        tmpltIdsSearch = createSearchBuilder();
        tmpltIdsSearch.and("idsIN", tmpltIdsSearch.entity().getId(), SearchCriteria.Op.IN);
        tmpltIdsSearch.groupBy(tmpltIdsSearch.entity().getId());
        tmpltIdsSearch.done();

        tmpltZoneSearch = createSearchBuilder();
        tmpltZoneSearch.and("id", tmpltZoneSearch.entity().getId(), SearchCriteria.Op.EQ);
        tmpltZoneSearch.and("dataCenterId", tmpltZoneSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        tmpltZoneSearch.and("state", tmpltZoneSearch.entity().getState(), SearchCriteria.Op.EQ);
        tmpltZoneSearch.done();

        activeTmpltSearch = createSearchBuilder();
        activeTmpltSearch.and("store_id", activeTmpltSearch.entity().getDataStoreId(), SearchCriteria.Op.EQ);
        activeTmpltSearch.and("type", activeTmpltSearch.entity().getTemplateType(), SearchCriteria.Op.EQ);
        activeTmpltSearch.and("templateState", activeTmpltSearch.entity().getTemplateState(), SearchCriteria.Op.EQ);
        activeTmpltSearch.and("public", activeTmpltSearch.entity().isPublicTemplate(), SearchCriteria.Op.EQ);
        activeTmpltSearch.done();

        // select distinct pair (template_id, zone_id)
        _count = "select count(distinct temp_zone_pair) from template_view WHERE ";
    }

    private String getTemplateStatus(TemplateJoinVO template) {
        String templateStatus = null;
        if (template.getDownloadState() != Status.DOWNLOADED) {
            templateStatus = "Processing";
            if (template.getDownloadState() == Status.DOWNLOAD_IN_PROGRESS) {
                if (template.getDownloadPercent() == 100) {
                    templateStatus = "Installing Template";
                } else {
                    templateStatus = template.getDownloadPercent() + "% Downloaded";
                }
            } else if (template.getDownloadState() == Status.BYPASSED) {
                templateStatus = "Bypassed Secondary Storage";
            }else if (template.getErrorString()==null){
                templateStatus = template.getTemplateState().toString();
            }else {
                templateStatus = template.getErrorString();
            }
        } else if (template.getDownloadState() == Status.DOWNLOADED) {
            templateStatus = "Download Complete";
        } else {
            templateStatus = "Successfully Installed";
        }
        return templateStatus;
    }

    @Override
    public TemplateResponse newTemplateResponse(EnumSet<ApiConstants.DomainDetails> detailsView, ResponseView view, TemplateJoinVO template) {
        List<TemplateDataStoreVO> templatesInStore = _templateStoreDao.listByTemplateNotBypassed(template.getId());
        List<Map<String, String>> downloadProgressDetails = new ArrayList();
        HashMap<String, String> downloadDetailInImageStores = null;
        for (TemplateDataStoreVO templateInStore : templatesInStore) {
            downloadDetailInImageStores = new HashMap<>();
            ImageStoreVO datastore = dataStoreDao.findById(templateInStore.getDataStoreId());
            if (datastore != null) {
                downloadDetailInImageStores.put("datastore", datastore.getName());
                downloadDetailInImageStores.put("downloadPercent", Integer.toString(templateInStore.getDownloadPercent()));
                downloadDetailInImageStores.put("downloadState", (templateInStore.getDownloadState() != null ? templateInStore.getDownloadState().toString() : ""));
                downloadProgressDetails.add(downloadDetailInImageStores);
            }
        }

        TemplateResponse templateResponse = new TemplateResponse();
        templateResponse.setDownloadProgress(downloadProgressDetails);
        templateResponse.setId(template.getUuid());
        templateResponse.setName(template.getName());
        templateResponse.setDisplayText(template.getDisplayText());
        templateResponse.setPublic(template.isPublicTemplate());
        templateResponse.setCreated(template.getCreatedOnStore());
        if (template.getFormat() == Storage.ImageFormat.BAREMETAL) {
            // for baremetal template, we didn't download, but is ready to use.
            templateResponse.setReady(true);
        } else {
            templateResponse.setReady(template.getState() == ObjectInDataStoreStateMachine.State.Ready);
        }
        templateResponse.setFeatured(template.isFeatured());
        templateResponse.setExtractable(template.isExtractable() && !(template.getTemplateType() == TemplateType.SYSTEM));
        templateResponse.setPasswordEnabled(template.isEnablePassword());
        templateResponse.setDynamicallyScalable(template.isDynamicallyScalable());
        templateResponse.setSshKeyEnabled(template.isEnableSshKey());
        templateResponse.setCrossZones(template.isCrossZones());
        templateResponse.setFormat(template.getFormat());
        if (template.getTemplateType() != null) {
            templateResponse.setTemplateType(template.getTemplateType().toString());
        }

        templateResponse.setHypervisor(template.getHypervisorType().toString());

        templateResponse.setOsTypeId(template.getGuestOSUuid());
        templateResponse.setOsTypeName(template.getGuestOSName());

        // populate owner.
        ApiResponseHelper.populateOwner(templateResponse, template);

        // populate domain
        templateResponse.setDomainId(template.getDomainUuid());
        templateResponse.setDomainName(template.getDomainName());

        // If the user is an 'Admin' or 'the owner of template' or template belongs to a project, add the template download status
        if (view == ResponseView.Full ||
                template.getAccountId() == CallContext.current().getCallingAccount().getId() ||
                template.getAccountType() == Account.ACCOUNT_TYPE_PROJECT) {
            String templateStatus = getTemplateStatus(template);
            if (templateStatus != null) {
                templateResponse.setStatus(templateStatus);
            }
            templateResponse.setUrl(template.getUrl());
        }

        if (template.getDataCenterId() > 0) {
            templateResponse.setZoneId(template.getDataCenterUuid());
            templateResponse.setZoneName(template.getDataCenterName());
        }

        Long templateSize = template.getSize();
        if (templateSize > 0) {
            templateResponse.setSize(templateSize);
        }

        Long templatePhysicalSize = template.getPhysicalSize();
        if (templatePhysicalSize > 0) {
            templateResponse.setPhysicalSize(templatePhysicalSize);
        }

        templateResponse.setChecksum(DigestHelper.getHashValueFromChecksumValue(template.getChecksum()));
        if (template.getSourceTemplateId() != null) {
            templateResponse.setSourceTemplateId(template.getSourceTemplateUuid());
        }
        templateResponse.setTemplateTag(template.getTemplateTag());

        if (template.getParentTemplateId() != null) {
            templateResponse.setParentTemplateId(template.getParentTemplateUuid());
        }

        // set details map
        if (detailsView.contains(ApiConstants.DomainDetails.all)) {
            Map<String, String> details = _templateDetailsDao.listDetailsKeyPairs(template.getId());
            templateResponse.setDetails(details);

            setDeployAsIsDetails(template, templateResponse);
        }

        // update tag information
        long tag_id = template.getTagId();
        if (tag_id > 0) {
            addTagInformation(template, templateResponse);
        }

        templateResponse.setDirectDownload(template.isDirectDownload());
        templateResponse.setDeployAsIs(template.isDeployAsIs());
        templateResponse.setRequiresHvm(template.isRequiresHvm());

        //set template children disks
        Set<ChildTemplateResponse> childTemplatesSet = new HashSet<ChildTemplateResponse>();
        if (template.getHypervisorType() == HypervisorType.VMware) {
            List<VMTemplateVO> childTemplates = _vmTemplateDao.listByParentTemplatetId(template.getId());
            for (VMTemplateVO tmpl : childTemplates) {
                if (tmpl.getTemplateType() != TemplateType.ISODISK) {
                    ChildTemplateResponse childTempl = new ChildTemplateResponse();
                    childTempl.setId(tmpl.getUuid());
                    childTempl.setName(tmpl.getName());
                    childTempl.setSize(Math.round(tmpl.getSize() / (1024 * 1024 * 1024)));
                    childTemplatesSet.add(childTempl);
                }
            }
            templateResponse.setChildTemplates(childTemplatesSet);
        }

        templateResponse.setObjectName("template");
        return templateResponse;
    }

    private void setDeployAsIsDetails(TemplateJoinVO template, TemplateResponse templateResponse) {
        if (template.isDeployAsIs()) {
            List<TemplateDeployAsIsDetailVO> deployAsIsDetails = templateDeployAsIsDetailsDao.listDetails(template.getId());
            for (TemplateDeployAsIsDetailVO deployAsIsDetailVO : deployAsIsDetails) {
                if (deployAsIsDetailVO.getName().startsWith(DeployAsIsConstants.HARDWARE_ITEM_PREFIX)) {
                    //Do not list hardware items
                    continue;
                }
                templateResponse.addDeployAsIsDetail(deployAsIsDetailVO.getName(), deployAsIsDetailVO.getValue());
            }
        }
    }

    //TODO: This is to keep compatibility with 4.1 API, where updateTemplateCmd and updateIsoCmd will return a simpler TemplateResponse
    // compared to listTemplates and listIsos.
    @Override
    public TemplateResponse newUpdateResponse(TemplateJoinVO result) {
        TemplateResponse response = new TemplateResponse();
        response.setId(result.getUuid());
        response.setName(result.getName());
        response.setDisplayText(result.getDisplayText());
        response.setPublic(result.isPublicTemplate());
        response.setCreated(result.getCreated());
        response.setFormat(result.getFormat());
        response.setOsTypeId(result.getGuestOSUuid());
        response.setOsTypeName(result.getGuestOSName());
        response.setBootable(result.isBootable());
        response.setHypervisor(result.getHypervisorType().toString());
        response.setDynamicallyScalable(result.isDynamicallyScalable());

        // populate owner.
        ApiResponseHelper.populateOwner(response, result);

        // populate domain
        response.setDomainId(result.getDomainUuid());
        response.setDomainName(result.getDomainName());

        // set details map
        if (result.getDetailName() != null) {
            Map<String, String> details = new HashMap<>();
            details.put(result.getDetailName(), result.getDetailValue());
            response.setDetails(details);
        }

        // update tag information
        long tag_id = result.getTagId();
        if (tag_id > 0) {
            ResourceTagJoinVO vtag = ApiDBUtils.findResourceTagViewById(tag_id);
            if (vtag != null) {
                response.addTag(ApiDBUtils.newResourceTagResponse(vtag, false));
            }
        }

        response.setObjectName("iso");
        return response;
    }

    @Override
    public TemplateResponse setTemplateResponse(EnumSet<ApiConstants.DomainDetails> detailsView, ResponseView view, TemplateResponse templateResponse, TemplateJoinVO template) {
        if (detailsView.contains(ApiConstants.DomainDetails.all)) {
            // update details map
            String key = template.getDetailName();
            if (key != null) {
                templateResponse.addDetail(key, template.getDetailValue());
            }
        }

        // update tag information
        long tag_id = template.getTagId();
        if (tag_id > 0) {
            addTagInformation(template, templateResponse);
        }

        return templateResponse;
    }

    @Override
    public TemplateResponse newIsoResponse(TemplateJoinVO iso) {

        TemplateResponse isoResponse = new TemplateResponse();
        isoResponse.setId(iso.getUuid());
        isoResponse.setName(iso.getName());
        isoResponse.setDisplayText(iso.getDisplayText());
        isoResponse.setPublic(iso.isPublicTemplate());
        isoResponse.setExtractable(iso.isExtractable() && !(iso.getTemplateType() == TemplateType.PERHOST));
        isoResponse.setCreated(iso.getCreatedOnStore());
        isoResponse.setDynamicallyScalable(iso.isDynamicallyScalable());
        if (iso.getTemplateType() == TemplateType.PERHOST) {
            // for TemplateManager.XS_TOOLS_ISO and TemplateManager.VMWARE_TOOLS_ISO, we didn't download, but is ready to use.
            isoResponse.setReady(true);
        } else {
            isoResponse.setReady(iso.getState() == ObjectInDataStoreStateMachine.State.Ready);
        }
        isoResponse.setBootable(iso.isBootable());
        isoResponse.setFeatured(iso.isFeatured());
        isoResponse.setCrossZones(iso.isCrossZones());
        isoResponse.setPublic(iso.isPublicTemplate());
        isoResponse.setChecksum(DigestHelper.getHashValueFromChecksumValue(iso.getChecksum()));

        isoResponse.setOsTypeId(iso.getGuestOSUuid());
        isoResponse.setOsTypeName(iso.getGuestOSName());
        isoResponse.setBits(iso.getBits());
        isoResponse.setPasswordEnabled(iso.isEnablePassword());

        // populate owner.
        ApiResponseHelper.populateOwner(isoResponse, iso);

        // populate domain
        isoResponse.setDomainId(iso.getDomainUuid());
        isoResponse.setDomainName(iso.getDomainName());

        Account caller = CallContext.current().getCallingAccount();
        boolean isAdmin = false;
        if ((caller == null) || _accountService.isAdmin(caller.getId())) {
            isAdmin = true;
        }

        // If the user is an admin, add the template download status
        if (isAdmin || caller.getId() == iso.getAccountId()) {
            // add download status
            if (iso.getDownloadState() != Status.DOWNLOADED) {
                String isoStatus = "Processing";
                if (iso.getDownloadState() == Status.DOWNLOADED) {
                    isoStatus = "Download Complete";
                } else if (iso.getDownloadState() == Status.DOWNLOAD_IN_PROGRESS) {
                    if (iso.getDownloadPercent() == 100) {
                        isoStatus = "Installing ISO";
                    } else {
                        isoStatus = iso.getDownloadPercent() + "% Downloaded";
                    }
                } else if (iso.getDownloadState() == Status.BYPASSED) {
                    isoStatus = "Bypassed Secondary Storage";
                } else {
                    isoStatus = iso.getErrorString();
                }
                isoResponse.setStatus(isoStatus);
            } else {
                isoResponse.setStatus("Successfully Installed");
            }
            isoResponse.setUrl(iso.getUrl());
        }

        if (iso.getDataCenterId() > 0) {
            isoResponse.setZoneId(iso.getDataCenterUuid());
            isoResponse.setZoneName(iso.getDataCenterName());
        }

        Long isoSize = iso.getSize();
        if (isoSize > 0) {
            isoResponse.setSize(isoSize);
        }

        // update tag information
        long tag_id = iso.getTagId();
        if (tag_id > 0) {
            ResourceTagJoinVO vtag = ApiDBUtils.findResourceTagViewById(tag_id);
            if (vtag != null) {
                isoResponse.addTag(ApiDBUtils.newResourceTagResponse(vtag, false));
            }
        }

        isoResponse.setDirectDownload(iso.isDirectDownload());

        isoResponse.setObjectName("iso");
        return isoResponse;

    }

    @Override
    public List<TemplateJoinVO> newTemplateView(VirtualMachineTemplate template) {
        SearchCriteria<TemplateJoinVO> sc = tmpltIdSearch.create();
        sc.setParameters("id", template.getId());
        return searchIncludingRemoved(sc, null, null, false);
    }

    @Override
    public List<TemplateJoinVO> newTemplateView(VirtualMachineTemplate template, long zoneId, boolean readyOnly) {
        SearchCriteria<TemplateJoinVO> sc = tmpltZoneSearch.create();
        sc.setParameters("id", template.getId());
        sc.setParameters("dataCenterId", zoneId);
        if (readyOnly) {
            sc.setParameters("state", TemplateState.Ready);
        }
        return searchIncludingRemoved(sc, null, null, false);
    }

    @Override
    public List<TemplateJoinVO> searchByTemplateZonePair(Boolean showRemoved, String... idPairs) {
        // set detail batch query size
        int DETAILS_BATCH_SIZE = 2000;
        String batchCfg = _configDao.getValue("detail.batch.query.size");
        if (batchCfg != null) {
            DETAILS_BATCH_SIZE = Integer.parseInt(batchCfg);
        }
        // query details by batches
        Filter searchFilter = new Filter(TemplateJoinVO.class, "sortKey", QueryService.SortKeyAscending.value(), null, null);
        searchFilter.addOrderBy(TemplateJoinVO.class, "tempZonePair", QueryService.SortKeyAscending.value());
        List<TemplateJoinVO> uvList = new ArrayList<TemplateJoinVO>();
        // query details by batches
        int curr_index = 0;
        if (idPairs.length > DETAILS_BATCH_SIZE) {
            while ((curr_index + DETAILS_BATCH_SIZE) <= idPairs.length) {
                String[] labels = new String[DETAILS_BATCH_SIZE];
                for (int k = 0, j = curr_index; j < curr_index + DETAILS_BATCH_SIZE; j++, k++) {
                    labels[k] = idPairs[j];
                }
                SearchCriteria<TemplateJoinVO> sc = tmpltIdPairSearch.create();
                if (!showRemoved) {
                    sc.setParameters("templateState", VirtualMachineTemplate.State.Active);
                }
                sc.setParameters("tempZonePairIN", labels);
                List<TemplateJoinVO> vms = searchIncludingRemoved(sc, searchFilter, null, false);
                if (vms != null) {
                    uvList.addAll(vms);
                }
                curr_index += DETAILS_BATCH_SIZE;
            }
        }
        if (curr_index < idPairs.length) {
            int batch_size = (idPairs.length - curr_index);
            String[] labels = new String[batch_size];
            for (int k = 0, j = curr_index; j < curr_index + batch_size; j++, k++) {
                labels[k] = idPairs[j];
            }
            SearchCriteria<TemplateJoinVO> sc = tmpltIdPairSearch.create();
            if (!showRemoved) {
                sc.setParameters("templateState", VirtualMachineTemplate.State.Active, VirtualMachineTemplate.State.UploadAbandoned, VirtualMachineTemplate.State.UploadError ,VirtualMachineTemplate.State.NotUploaded, VirtualMachineTemplate.State.UploadInProgress);
            }
            sc.setParameters("tempZonePairIN", labels);
            List<TemplateJoinVO> vms = searchIncludingRemoved(sc, searchFilter, null, false);
            if (vms != null) {
                uvList.addAll(vms);
            }
        }
        return uvList;
    }

    @Override
    public List<TemplateJoinVO> listActiveTemplates(long storeId) {
        SearchCriteria<TemplateJoinVO> sc = activeTmpltSearch.create();
        sc.setParameters("store_id", storeId);
        sc.setParameters("type", TemplateType.USER);
        sc.setParameters("templateState", VirtualMachineTemplate.State.Active);
        sc.setParameters("public", Boolean.FALSE);
        return searchIncludingRemoved(sc, null, null, false);
    }

    @Override
    public Pair<List<TemplateJoinVO>, Integer> searchIncludingRemovedAndCount(final SearchCriteria<TemplateJoinVO> sc, final Filter filter) {
        List<TemplateJoinVO> objects = searchIncludingRemoved(sc, filter, null, false);
        Integer count = getCountIncludingRemoved(sc);
        return new Pair<List<TemplateJoinVO>, Integer>(objects, count);
    }

    @Override
    public List<TemplateJoinVO> findByDistinctIds(Long... ids) {
        if (ids == null || ids.length == 0) {
            return new ArrayList<TemplateJoinVO>();
        }
        SearchCriteria<TemplateJoinVO> sc = tmpltIdsSearch.create();
        sc.setParameters("idsIN", ids);
        return searchIncludingRemoved(sc, null, null, false);
    }

}
