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

<template>
  <a-row :gutter="12" v-if="isPageAllowed">
    <a-col :md="24">
      <a-card class="breadcrumb-card">
        <a-col :md="24" style="display: flex">
          <breadcrumb style="padding-top: 6px; padding-left: 8px" />
          <a-button
            style="margin-left: 12px; margin-top: 4px"
            :loading="viewLoading"
            icon="reload"
            size="small"
            shape="round"
            @click="fetchData()" >
            {{ $t('label.refresh') }}
          </a-button>
        </a-col>
      </a-card>
    </a-col>
    <a-col
      :md="24">
      <div>
        <a-card>
          <a-alert type="info" :showIcon="true" :message="$t('label.desc.importexportinstancewizard')" :description="$t('message.desc.importexportinstancewizard')" />
          <br />
          <a-form
            style="min-width: 170px"
            :form="form"
            layout="vertical">
            <a-col :md="24" :lg="8">
              <a-form-item :label="this.$t('label.zoneid')">
                <a-select
                  v-decorator="['zoneid', {}]"
                  showSearch
                  optionFilterProp="children"
                  :filterOption="filterOption"
                  :options="zoneSelectOptions"
                  @change="onSelectZoneId"
                  :loading="optionLoading.zones"
                  autoFocus
                ></a-select>
              </a-form-item>
            </a-col>
            <a-col :md="24" :lg="8">
              <a-form-item
                :label="this.$t('label.podid')">
                <a-select
                  v-decorator="['podid']"
                  showSearch
                  optionFilterProp="children"
                  :filterOption="filterOption"
                  :options="podSelectOptions"
                  :loading="optionLoading.pods"
                  @change="onSelectPodId"
                ></a-select>
              </a-form-item>
            </a-col>
            <a-col :md="24" :lg="8">
              <a-form-item
                :label="this.$t('label.clusterid')">
                <a-select
                  v-decorator="['clusterid']"
                  showSearch
                  optionFilterProp="children"
                  :filterOption="filterOption"
                  :options="clusterSelectOptions"
                  :loading="optionLoading.clusters"
                  @change="onSelectClusterId"
                ></a-select>
              </a-form-item>
            </a-col>
          </a-form>
          <a-divider />
          <a-row :gutter="12">
            <a-col :md="24" :lg="12">
              <a-card class="instances-card">
                <span slot="title">
                  {{ $t('label.unmanaged.instances') }}
                  <a-tooltip :title="$t('message.instances.unmanaged')">
                    <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
                  </a-tooltip>
                  <a-button
                    style="margin-left: 12px; margin-top: 4px"
                    :loading="unmanagedInstancesLoading"
                    icon="reload"
                    size="small"
                    shape="round"
                    @click="fetchUnmanagedInstances()" >
                  </a-button>
                  <span style="float: right; width: 50%">
                    <search-view
                      :searchFilters="searchFilters.unmanaged"
                      :searchParams="searchParams.unmanaged"
                      :apiName="listInstancesApi.unmanaged"
                      @search="searchUnmanagedInstances"
                    />
                  </span>
                </span>
                <a-table
                  class="instances-card-table"
                  :loading="unmanagedInstancesLoading"
                  :rowSelection="unmanagedInstanceSelection"
                  :rowKey="(record, index) => index"
                  :columns="unmanagedInstancesColumns"
                  :data-source="unmanagedInstances"
                  :pagination="false"
                  size="middle"
                  :rowClassName="getRowClassName"
                >
                  <template slot="state" slot-scope="text">
                    <status :text="text ? text : ''" displayText />
                  </template>
                </a-table>
                <div class="instances-card-footer">
                  <a-pagination
                    class="row-element"
                    size="small"
                    :current="page.unmanaged"
                    :pageSize="pageSize.unmanaged"
                    :total="itemCount.unmanaged"
                    :showTotal="total => `${$t('label.showing')} ${Math.min(total, 1+((page.unmanaged-1)*pageSize.unmanaged))}-${Math.min(page.unmanaged*pageSize.unmanaged, total)} ${$t('label.of')} ${total} ${$t('label.items')}`"
                    @change="fetchUnmanagedInstances"
                    showQuickJumper>
                    <template slot="buildOptionText" slot-scope="props">
                      <span>{{ props.value }} / {{ $t('label.page') }}</span>
                    </template>
                  </a-pagination>
                  <div :span="24" class="action-button-right">
                    <a-button
                      :loading="importUnmanagedInstanceLoading"
                      :disabled="!(('importUnmanagedInstance' in $store.getters.apis) && unmanagedInstancesSelectedRowKeys.length > 0)"
                      type="primary"
                      icon="import"
                      @click="onManageInstanceAction">
                      {{ $t('label.import.instance') }}
                    </a-button>
                  </div>
                </div>
              </a-card>
            </a-col>
            <a-col :md="24" :lg="12">
              <a-card class="instances-card">
                <span slot="title">
                  {{ $t('label.managed.instances') }}
                  <a-tooltip :title="$t('message.instances.managed')">
                    <a-icon type="info-circle" style="color: rgba(0,0,0,.45)" />
                  </a-tooltip>
                  <a-button
                    style="margin-left: 12px; margin-top: 4px"
                    :loading="managedInstancesLoading"
                    icon="reload"
                    size="small"
                    shape="round"
                    @click="fetchManagedInstances()" >
                  </a-button>
                  <span style="float: right; width: 50%">
                    <search-view
                      :searchFilters="searchFilters.managed"
                      :searchParams="searchParams.managed"
                      :apiName="listInstancesApi.managed"
                      @search="searchManagedInstances"
                    />
                  </span>
                </span>
                <a-table
                  class="instances-card-table"
                  :loading="managedInstancesLoading"
                  :rowSelection="managedInstanceSelection"
                  :rowKey="(record, index) => index"
                  :columns="managedInstancesColumns"
                  :data-source="managedInstances"
                  :pagination="false"
                  size="middle"
                  :rowClassName="getRowClassName"
                >
                  <a slot="name" slot-scope="text, record" href="javascript:;">
                    <router-link :to="{ path: '/vm/' + record.id }">{{ text }}</router-link>
                  </a>
                  <template slot="state" slot-scope="text">
                    <status :text="text ? text : ''" displayText />
                  </template>
                </a-table>
                <div class="instances-card-footer">
                  <a-pagination
                    class="row-element"
                    size="small"
                    :current="page.managed"
                    :pageSize="pageSize.managed"
                    :total="itemCount.managed"
                    :showTotal="total => `${$t('label.showing')} ${Math.min(total, 1+((page.managed-1)*pageSize.managed))}-${Math.min(page.managed*pageSize.managed, total)} ${$t('label.of')} ${total} ${$t('label.items')}`"
                    @change="fetchManagedInstances"
                    showQuickJumper>
                    <template slot="buildOptionText" slot-scope="props">
                      <span>{{ props.value }} / {{ $t('label.page') }}</span>
                    </template>
                  </a-pagination>
                  <div :span="24" class="action-button-right">
                    <a-button
                      :disabled="!(('unmanageVirtualMachine' in $store.getters.apis) && managedInstancesSelectedRowKeys.length > 0)"
                      type="primary"
                      icon="disconnect"
                      @click="onUnmanageInstanceAction">
                      {{ managedInstancesSelectedRowKeys.length > 1 ? $t('label.action.unmanage.instances') : $t('label.action.unmanage.instance') }}
                    </a-button>
                  </div>
                </div>
              </a-card>
            </a-col>
          </a-row>
        </a-card>

        <a-modal
          :visible="showUnmanageForm"
          :title="$t('label.import.instance')"
          :closable="true"
          :maskClosable="false"
          :footer="null"
          :cancelText="$t('label.cancel')"
          @cancel="showUnmanageForm = false"
          centered
          ref="importModal"
          width="auto">
          <import-unmanaged-instances
            class="importform"
            :resource="selectedUnmanagedInstance"
            :cluster="selectedCluster"
            :isOpen="showUnmanageForm"
            @refresh-data="fetchInstances"
            @close-action="closeImportUnmanagedInstanceForm"
            @loading-changed="updateManageInstanceActionLoading"
          />
        </a-modal>
      </div>
    </a-col>
  </a-row>
</template>

<script>
import { api } from '@/api'
import _ from 'lodash'
import Breadcrumb from '@/components/widgets/Breadcrumb'
import Status from '@/components/widgets/Status'
import SearchView from '@/components/view/SearchView'
import ImportUnmanagedInstances from '@/views/tools/ImportUnmanagedInstance'

export default {
  components: {
    Breadcrumb,
    Status,
    SearchView,
    ImportUnmanagedInstances
  },
  name: 'ManageVms',
  data () {
    const unmanagedInstancesColumns = [
      {
        title: this.$t('label.name'),
        dataIndex: 'name',
        width: 100
      },
      {
        title: this.$t('label.state'),
        dataIndex: 'powerstate',
        scopedSlots: { customRender: 'state' }
      },
      {
        title: this.$t('label.hostname'),
        dataIndex: 'hostname'
      },
      {
        title: this.$t('label.ostypename'),
        dataIndex: 'osdisplayname'
      }
    ]
    const managedInstancesColumns = [
      {
        title: this.$t('label.name'),
        dataIndex: 'name',
        width: 100,
        scopedSlots: { customRender: 'name' }
      },
      {
        title: this.$t('label.instancename'),
        dataIndex: 'instancename'
      },
      {
        title: this.$t('label.state'),
        dataIndex: 'state',
        scopedSlots: { customRender: 'state' }
      },
      {
        title: this.$t('label.hostname'),
        dataIndex: 'hostname'
      },
      {
        title: this.$t('label.templatename'),
        dataIndex: 'templatedisplaytext'
      }
    ]
    return {
      options: {
        zones: [],
        pods: [],
        clusters: []
      },
      rowCount: {},
      optionLoading: {
        zones: false,
        pods: false,
        clusters: false
      },
      page: {
        unmanaged: 1,
        managed: 1
      },
      pageSize: {
        unmanaged: 10,
        managed: 10
      },
      searchFilters: {
        unmanaged: [],
        managed: []
      },
      searchParams: {
        unmanaged: {},
        managed: {}
      },
      itemCount: {},
      zone: {},
      zoneId: undefined,
      podId: undefined,
      clusterId: undefined,
      listInstancesApi: {
        unmanaged: 'listUnmanagedInstances',
        managed: 'listVirtualMachines'
      },
      unmanagedInstancesColumns,
      unmanagedInstancesLoading: false,
      unmanagedInstances: [],
      unmanagedInstancesSelectedRowKeys: [],
      importUnmanagedInstanceLoading: false,
      managedInstancesColumns,
      managedInstancesLoading: false,
      managedInstances: [],
      managedInstancesSelectedRowKeys: [],
      showUnmanageForm: false,
      selectedUnmanagedInstance: {},
      query: {}
    }
  },
  beforeCreate () {
    this.form = this.$form.createForm(this)
  },
  created () {
    this.page.unmanaged = parseInt(this.$route.query.unmanagedpage || 1)
    this.page.managed = parseInt(this.$route.query.managedpage || 1)
    this.fetchData()
  },
  computed: {
    isPageAllowed () {
      if (this.$route.meta.permission) {
        for (var apiName of this.$route.meta.permission) {
          if (!(apiName in this.$store.getters.apis)) {
            return false
          }
        }
      }
      return true
    },
    params () {
      return {
        zones: {
          list: 'listZones',
          isLoad: true,
          field: 'zoneid'
        },
        pods: {
          list: 'listPods',
          isLoad: false,
          options: {
            zoneid: _.get(this.zone, 'id')
          },
          field: 'podid'
        },
        clusters: {
          list: 'listClusters',
          isLoad: false,
          options: {
            zoneid: _.get(this.zone, 'id'),
            podid: this.podId
          },
          field: 'clusterid'
        }
      }
    },
    viewLoading () {
      for (var key in this.optionLoading) {
        if (this.optionLoading[key]) {
          return true
        }
      }
      return this.unmanagedInstancesLoading || this.managedInstancesLoading
    },
    zoneSelectOptions () {
      return this.options.zones.map((zone) => {
        return {
          label: zone.name,
          value: zone.id
        }
      })
    },
    podSelectOptions () {
      const options = this.options.pods.map((pod) => {
        return {
          label: pod.name,
          value: pod.id
        }
      })
      return options
    },
    clusterSelectOptions () {
      const options = this.options.clusters.map((cluster) => {
        return {
          label: cluster.name,
          value: cluster.id
        }
      })
      return options
    },
    unmanagedInstanceSelection () {
      return {
        type: 'radio',
        selectedRowKeys: this.unmanagedInstancesSelectedRowKeys || [],
        onChange: this.onUnmanagedInstanceSelectRow
      }
    },
    managedInstanceSelection () {
      return {
        type: 'checkbox',
        selectedRowKeys: this.managedInstancesSelectedRowKeys || [],
        onChange: this.onManagedInstanceSelectRow
      }
    },
    selectedCluster () {
      if (this.options.clusters &&
        this.options.clusters.length > 0 &&
        this.clusterId) {
        return _.find(this.options.clusters, (option) => option.id === this.clusterId)
      }
      return {}
    }
  },
  methods: {
    fetchData () {
      this.unmanagedInstances = []
      this.managedInstances = []
      _.each(this.params, (param, name) => {
        if (param.isLoad) {
          this.fetchOptions(param, name)
        }
      })
    },
    filterOption (input, option) {
      return (
        option.componentOptions.children[0].text.toUpperCase().indexOf(input.toUpperCase()) >= 0
      )
    },
    fetchOptions (param, name, exclude) {
      if (exclude && exclude.length > 0) {
        if (exclude.includes(name)) {
          return
        }
      }
      this.optionLoading[name] = true
      param.loading = true
      param.opts = []
      const options = param.options || {}
      if (!('listall' in options)) {
        options.listall = true
      }
      api(param.list, options).then((response) => {
        param.loading = false
        _.map(response, (responseItem, responseKey) => {
          if (Object.keys(responseItem).length === 0) {
            this.rowCount[name] = 0
            this.options[name] = []
            this.$forceUpdate()
            return
          }
          if (!responseKey.includes('response')) {
            return
          }
          _.map(responseItem, (response, key) => {
            if (key === 'count') {
              this.rowCount[name] = response
              return
            }
            param.opts = response
            this.options[name] = response

            this.$forceUpdate()
          })
          this.handleFetchOptionsSuccess(name, param)
        })
      }).catch(function (error) {
        console.log(error.stack)
        param.loading = false
      }).finally(() => {
        this.optionLoading[name] = false
      })
    },
    getRowClassName (record, index) {
      if (index % 2 === 0) {
        return 'light-row'
      }
      return 'dark-row'
    },
    handleFetchOptionsSuccess (name, param) {
      if (['zones', 'pods', 'clusters'].includes(name)) {
        let paramid = ''
        const query = Object.assign({}, this.$route.query)
        if (query[param.field] && _.find(this.options[name], (option) => option.id === query[param.field])) {
          paramid = query[param.field]
        }
        if (!paramid && this.options[name].length === 1) {
          paramid = (this.options[name])[0].id
        }
        if (paramid) {
          this.form.getFieldDecorator([param.field], { initialValue: paramid })
          if (name === 'zones') {
            this.onSelectZoneId(paramid)
          } else if (name === 'pods') {
            this.form.setFieldsValue({
              podid: paramid
            })
            this.onSelectPodId(paramid)
          } else if (name === 'clusters') {
            this.form.setFieldsValue({
              clusterid: paramid
            })
            this.onSelectClusterId(paramid)
          }
        }
      }
    },
    updateQuery (field, value) {
      const query = Object.assign({}, this.$route.query)
      if (query[field] === value + '') {
        return
      }
      query[field] = value
      if (['zoneid', 'podid', 'clusterid'].includes(field)) {
        query.managedpage = 1
        query.unmanagedpage = 1
      }
      this.$router.push({ query })
    },
    resetLists () {
      this.page.unmanaged = 1
      this.unmanagedInstances = []
      this.unmanagedInstancesSelectedRowKeys = []
      this.page.managed = 1
      this.managedInstances = []
      this.managedInstancesSelectedRowKeys = []
    },
    onSelectZoneId (value) {
      this.zoneId = value
      this.podId = null
      this.clusterId = null
      this.zone = _.find(this.options.zones, (option) => option.id === value)
      this.resetLists()
      this.form.setFieldsValue({
        clusterid: undefined,
        podid: undefined
      })
      this.updateQuery('zoneid', value)
      this.fetchOptions(this.params.pods, 'pods')
    },
    onSelectPodId (value) {
      this.podId = value
      this.resetLists()
      this.form.setFieldsValue({
        clusterid: undefined
      })
      this.updateQuery('podid', value)
      this.fetchOptions(this.params.clusters, 'clusters', value)
    },
    onSelectClusterId (value) {
      this.clusterId = value
      this.resetLists()
      this.updateQuery('clusterid', value)
      this.fetchInstances()
    },
    fetchInstances () {
      if (this.selectedCluster.hypervisortype === 'VMware') {
        this.fetchUnmanagedInstances()
        this.fetchManagedInstances()
      }
    },
    fetchUnmanagedInstances (page, pageSize) {
      const params = {
        clusterid: this.clusterId
      }
      const query = Object.assign({}, this.$route.query)
      this.page.unmanaged = page || parseInt(query.unmanagedpage) || this.page.unmanaged
      this.updateQuery('unmanagedpage', this.page.unmanaged)
      params.page = this.page.unmanaged
      this.pageSize.unmanaged = pageSize || this.pageSize.unmanaged
      params.pagesize = this.pageSize.unmanaged
      this.unmanagedInstances = []
      this.unmanagedInstancesSelectedRowKeys = []
      if (this.searchParams.unmanaged.keyword) {
        params.keyword = this.searchParams.unmanaged.keyword
      }
      if (!this.clusterId) {
        return
      }
      this.unmanagedInstancesLoading = true
      this.searchParams.unmanaged = params
      api(this.listInstancesApi.unmanaged, params).then(json => {
        const listUnmanagedInstances = json.listunmanagedinstancesresponse.unmanagedinstance
        if (this.arrayHasItems(listUnmanagedInstances)) {
          this.unmanagedInstances = this.unmanagedInstances.concat(listUnmanagedInstances)
        }
        this.itemCount.unmanaged = json.listunmanagedinstancesresponse.count
      }).finally(() => {
        this.unmanagedInstancesLoading = false
      })
    },
    searchUnmanagedInstances (params) {
      this.searchParams.unmanaged.keyword = params.searchQuery
      this.fetchUnmanagedInstances()
    },
    fetchManagedInstances (page, pageSize) {
      const params = {
        listall: true,
        clusterid: this.clusterId
      }
      const query = Object.assign({}, this.$route.query)
      this.page.managed = page || parseInt(query.managedpage) || this.page.managed
      this.updateQuery('managedpage', this.page.managed)
      params.page = this.page.managed
      this.pageSize.managed = pageSize || this.pageSize.managed
      params.pagesize = this.pageSize.managed
      this.managedInstances = []
      this.managedInstancesSelectedRowKeys = []
      if (this.searchParams.managed.keyword) {
        params.keyword = this.searchParams.managed.keyword
      }
      if (!this.clusterId) {
        return
      }
      this.managedInstancesLoading = true
      this.searchParams.managed = params
      api(this.listInstancesApi.managed, params).then(json => {
        const listManagedInstances = json.listvirtualmachinesresponse.virtualmachine
        if (this.arrayHasItems(listManagedInstances)) {
          this.managedInstances = this.managedInstances.concat(listManagedInstances)
        }
        this.itemCount.managed = json.listvirtualmachinesresponse.count
      }).finally(() => {
        this.managedInstancesLoading = false
      })
    },
    searchManagedInstances (params) {
      this.searchParams.managed.keyword = params.searchQuery
      this.fetchManagedInstances()
    },
    onUnmanagedInstanceSelectRow (value) {
      this.unmanagedInstancesSelectedRowKeys = value
    },
    onManagedInstanceSelectRow (value) {
      this.managedInstancesSelectedRowKeys = value
    },
    isValidValueForKey (obj, key) {
      return key in obj && obj[key] != null
    },
    arrayHasItems (array) {
      return array !== null && array !== undefined && Array.isArray(array) && array.length > 0
    },
    isObjectEmpty (obj) {
      return !(obj !== null && obj !== undefined && Object.keys(obj).length > 0 && obj.constructor === Object)
    },
    updateManageInstanceActionLoading (value) {
      this.importUnmanagedInstanceLoading = value
    },
    onManageInstanceAction () {
      this.selectedUnmanagedInstance = {}
      if (this.unmanagedInstances.length > 0 &&
        this.unmanagedInstancesSelectedRowKeys.length > 0) {
        this.selectedUnmanagedInstance = this.unmanagedInstances[this.unmanagedInstancesSelectedRowKeys[0]]
        this.selectedUnmanagedInstance.ostypename = this.selectedUnmanagedInstance.osdisplayname
        this.selectedUnmanagedInstance.state = this.selectedUnmanagedInstance.powerstate
      }
      this.showUnmanageForm = true
    },
    closeImportUnmanagedInstanceForm () {
      this.selectedUnmanagedInstance = {}
      this.showUnmanageForm = false
      this.$refs.importModal.$forceUpdate()
    },
    onUnmanageInstanceAction () {
      const self = this
      const title = this.managedInstancesSelectedRowKeys.length > 1
        ? this.$t('message.action.unmanage.instances')
        : this.$t('message.action.unmanage.instance')
      var vmNames = []
      for (var index of this.managedInstancesSelectedRowKeys) {
        vmNames.push(this.managedInstances[index].name)
      }
      const content = vmNames.join(', ')
      this.$confirm({
        title: title,
        okText: this.$t('label.ok'),
        okType: 'danger',
        content: content,
        cancelText: this.$t('label.cancel'),
        onOk () {
          self.unmanageInstances()
        }
      })
    },
    unmanageInstances () {
      for (var index of this.managedInstancesSelectedRowKeys) {
        const vm = this.managedInstances[index]
        var params = { id: vm.id }
        api('unmanageVirtualMachine', params).then(json => {
          const jobId = json.unmanagevirtualmachineresponse.jobid
          this.$pollJob({
            jobId,
            title: this.$t('label.unmanage.instance'),
            description: vm.name,
            loadingMessage: `${this.$t('label.unmanage.instance')} ${vm.name} ${this.$t('label.in.progress')}`,
            catchMessage: this.$t('error.fetching.async.job.result'),
            successMessage: this.$t('message.success.unmanage.instance') + ' ' + vm.name,
            successMethod: result => {
              if (index === this.managedInstancesSelectedRowKeys[this.managedInstancesSelectedRowKeys.length - 1]) {
                this.fetchInstances()
              }
            }
          })
        }).catch(error => {
          this.$notifyError(error)
        }).finally(() => {
          this.loading = false
        })
      }
    }
  }
}
</script>

<style scoped>
/deep/ .ant-table-thead {
  background-color: #f9f9f9;
}

/deep/ .ant-table-small > .ant-table-content > .ant-table-body {
  margin: 0;
}

/deep/ .light-row {
  background-color: #fff;
}

/deep/ .dark-row {
  background-color: #f9f9f9;
}
</style>

<style scoped lang="less">
  .importform {
    width: 80vw;
  }
  .instances-card {
    height: 100%;
  }
  .instances-card-table {
    overflow-y: auto;
    margin-bottom: 100px;
  }
  .instances-card-footer {
    height: 100px;
    position: absolute;
    bottom: 0;
    left: 0;
    margin-left: 10px;
    right: 0;
    margin-right: 10px;
  }
  .row-element {
    margin-top: 10px;
    margin-bottom: 10px;
  }
  .action-button-left {
    text-align: left;
  }
  .action-button-right {
    text-align: right;
  }

  .breadcrumb-card {
  margin-left: -24px;
  margin-right: -24px;
  margin-top: -16px;
  margin-bottom: 12px;
}

.ant-breadcrumb {
  vertical-align: text-bottom;
}

</style>
