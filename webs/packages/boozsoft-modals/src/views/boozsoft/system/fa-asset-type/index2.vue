<template>
  <div>
    <div class="app-container">
      <div class="app-container-head">
        <div class="container-head-title">
          <UnorderedListOutlined style="color: #0096c7"/>
          <b class="noneSpan">资产类别</b>
        </div>
        <div class="ant-btn-group" style="float: right">
          <button
            type="button"
            class="ant-btn ant-btn-me"
            ant-click-animating-without-extra-node="false"
            @click="openAddPage()"
          ><span>新建</span></button>
          <button
            type="button"
            class="ant-btn ant-btn-me"
            ant-click-animating-without-extra-node="false"
            @click="editOpen()"
          ><span>修改</span></button>
          <button
            type="button"
            class="ant-btn ant-btn-me"
            ant-click-animating-without-extra-node="false"
            @click="delList()"
          ><span>删除</span></button>
          <button
            type="button"
            class="ant-btn ant-btn-me"
            @click="openExcel()"
          ><span>导入</span></button>
          <button
            type="button"
            class="ant-btn ant-btn-me"
            @click="exportExcelNow()"
          ><span>导出</span></button>
          <button
            type="button"
            class="ant-btn ant-btn-me"
            ant-click-animating-without-extra-node="false"
            @click="openPrint()"
          ><span>打印</span></button>
          <button
            type="button"
            class="ant-btn ant-btn-me"
            @click="closeCurrent(),router.push('/one/home/welcome')">
            <span>退出</span></button>
        </div>
      </div>
      <div class="app-container-neck">
        <!--        <UnitChange v-if="!defaultPage" @reloadTable="dynamicAdReload" v-model="pageParameter"/>-->
        <div style="display: inline-block;float: left;font-size: 14px;">
          <AccountPicker theme="three" @reloadTable="dynamicAdReload"/>

        </div>
        <div style="display: inline-block;float: left;font-size: 14px;padding-top: 5px;padding-left: 20px;">
          <span> 共{{totalData}}条</span>
        </div>
        <div style="float: right; margin-left: 10px">
          <a-button @click="reloadTable()">
            <SyncOutlined :style="{ fontSize: '14px' }"/>
          </a-button>
          <a-popover class="ant-btn-default" placement="bottom">
            <template #content>
              <span class="group-btn-span-special2" @click="pageParameter.showRulesSize = 'MAX'"
                    :style="pageParameter.showRulesSize==='MAX'?{backgroundColor: '#0096c7',color: 'white'}:''">
                <SortDescendingOutlined/>&nbsp;大号字体&ensp;<CheckOutlined
                v-if="pageParameter.showRulesSize==='MAX'"/></span><br/>
              <span class="group-btn-span-special2" @click="pageParameter.showRulesSize = 'MIN'"
                    :style="pageParameter.showRulesSize==='MIN'?{backgroundColor: '#0096c7',color: 'white'}:''">
                <SortAscendingOutlined/>&nbsp;小号字体&ensp;<CheckOutlined
                v-if="pageParameter.showRulesSize==='MIN'"/></span>
            </template>
            <!--            <template #title>
                          <b>设置表格字号</b>
                        </template>-->
            <a-button>
              <SettingFilled :style="{ fontSize: '14px' }"/>
            </a-button>
          </a-popover>
          <a-popover class="ant-btn-default" placement="bottom">
            <template #content>
            <span class="group-btn-span-special2" @click="onChangeSwitch('')"
                  :style="flag==''?{backgroundColor: '#0096c7',color: 'white'}:''">
                <ReadOutlined/>&nbsp;&emsp;全部&emsp;&ensp;<CheckOutlined
              v-if="flag==''"/></span><br/>
              <span class="group-btn-span-special2" @click="onChangeSwitch('1')"
                    :style="flag=='1'?{backgroundColor: '#0096c7',color: 'white'}:''">
                <SafetyOutlined/>&nbsp;&emsp;启用&emsp;&ensp;<CheckOutlined
                v-if="flag=='1'"/></span><br/>
              <span class="group-btn-span-special2" @click="onChangeSwitch('0')"
                    :style="flag=='0'?{backgroundColor: '#0096c7',color: 'white'}:''">
                <RestOutlined/>&nbsp;&emsp;停用&emsp;&ensp;<CheckOutlined
                v-if="flag=='0'"/></span>
            </template>
            <a-button>
              <PicLeftOutlined :style="{ fontSize: '14px' }"/>
            </a-button>
          </a-popover>

        </div>
        <div style="float: right; position: relative">
          <a-select v-model:value="formItems.selectType" style="width: 120px;font-size: 12px;" class="special_select">
            <a-select-option style="font-size: 12px;" value="ecCode">资产类别编码</a-select-option>
            <a-select-option style="font-size: 12px;" value="ecName">资产类别名称</a-select-option>
          </a-select>
          <a-input-search
            placeholder=""
            style="width: 200px; border-radius: 4px"
            v-model:value="formItems.selectValue"
            @search="onSearch"
          />
        </div>
      </div>
    </div>
    <div class="app-container">
      <EditPage @save="saveData" @register="registerEditPage"/>
      <AddPage @save="saveData" @register="registerSavePage"/>
      <Excel @save="saveExcel" @register="registerExcelPage"/>
      <Print @save="loadPrint" @register="registerPrintPage"/>
      <Loading :loading="compState.loading" :absolute="compState.absolute" :tip="compState.tip" />
      <PageWrapper dense content-full-height fixed-height content-class="flex" style="height: 600px">
        <div  style="width: 250px;height: 100%;border: 1px #cccccc solid;margin-right: .2%;margin-bottom: 58px;">
          <DeptTree v-if="isShowTree"  @select="handleSelect" v-model="pageParameter" ref="mychild"/>
        </div>

        <div class="bg-white" style="width: calc(100% - 250px); float: right;margin-left: 5px;margin-top: -0.5px;">
          <BasicTable
                      ref="tableRef"
                      :loading="loadMark"
                      :class="pageParameter.showRulesSize=='MAX'?'a-table-font-size-16':'a-table-font-size-12'"
                      :row-selection="{ type: 'checkbox', selectedRowKeys: state.selectedRowKeys, onChange: onSelectChange }"
                      :scroll="{ x: totalColumnWidth,y: windowHeight }"
                      :dataSource="tableData"
                      @row-click="condClick"
                      @register="registerTable">

            <template #ecCode="{ record }" class="a-table-money-font-size a-table-font-arial">
                <span style="float: left" >{{ record.ecCode }}</span>
            </template>

            <template #ecName="{ record }" class="a-table-money-font-size a-table-font-arial">
              <span style="float: left"  v-if=" record.ecCode.length > 1 && record.ecCode.length < 4">&emsp;&emsp;{{ record.ecName }}</span>
              <span style="float: left"  v-if=" record.ecCode.length > 3 && record.ecCode.length < 6">&emsp;&emsp;&emsp;&emsp;{{ record.ecName }}</span>
              <span style="float: left" v-if=" record.ecCode.length < 3 ">{{ record.ecName }}</span>
            </template>

            <template #aname="{ record }" class="a-table-money-font-size a-table-font-arial">
              <span style="float: left" >{{ record.aname }}</span>
            </template>

            <template #uname="{ record }" class="a-table-money-font-size a-table-font-arial">
              <span style="float: left" >{{ record.uname }}</span>
            </template>

            <template #mname="{ record }" class="a-table-money-font-size a-table-font-arial">
              <span style="float: left" >{{ record.mname }}</span>
            </template>

            <template #inputTax="{ record }" >
              <span  >{{ record.inputTax}}%</span>
            </template>

            <template #netSalvage="{ record }" >
              <span  >{{ record.netSalvage}}%</span>
            </template>

            <template #flag="{ record }">
              <span>
                <a-tag :color="record.flag === '1' ? 'green' : 'volcano'">
                  {{ record.flag === '1' ? '启用' : '停用' }}
                </a-tag>
              </span>
            </template>
            <template #zjType="{ record }">
              <span>
                <a-tag :color="record.zjType === '1' ? 'blue' : 'cyan'">
                  {{ record.zjType === '1' ? '系统' : '自定义' }}
                </a-tag>
              </span>
            </template>
            <template #action="{ record }">
              <div>
                <a-popover placement="bottom">
                  <a-button style="padding: 0px 4px; height: 20px;">
                    <CaretDownFilled />
                  </a-button>
                  <template #content>
                    <!--              <p v-if="record.flag=='1'" style="cursor: pointer" @click="condClick(record)"><FormOutlined /> 编辑</p>-->
                    <p v-if="record.flag=='0'" class="p_specifics" style="cursor: pointer"
                       @click="editFlagData(record)">
                      <CheckCircleOutlined/>
                      启用
                    </p>
                    <p v-if="record.flag=='1'" class="p_specifics" style="cursor: pointer"
                       @click="editFlagData(record)">
                      <CloseCircleOutlined/>
                      停用
                    </p>
                    <!--              <p style="cursor: pointer" @click="del(record)"><DeleteOutlined /> 删除</p>-->
                  </template>
                </a-popover>
              </div>
            </template>
          </BasicTable>
        </div>
      </PageWrapper>
    </div>
  </div>
</template>
<script setup lang="ts">
import {deleteDept, saveDept, editFlag, getDeptListById, excelDept,getTotalData} from '/@/api/record/system/fa-asset-type'
import { BasicTable, useTable } from '/@/components/Table'
import EditPage from './popup/edit.vue'
import AddPage from './popup/save.vue'
import DeptTree from './DeptTree.vue'
import Print from './popup/print.vue'
import Excel from './popup/excel.vue'
import { useModal } from '/@/components/Modal'
import { PageWrapper } from '/@/components/Page'
import { Loading } from '/@/components/Loading';
import {
  CaretDownFilled,
  FormOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SettingFilled,
  SyncOutlined,
  PicLeftOutlined,
  EditFilled,
  PieChartFilled,
  FilterFilled, SortAscendingOutlined, SortDescendingOutlined, CheckOutlined,
  ReadOutlined, SafetyOutlined, RestOutlined,PicRightOutlined,UnorderedListOutlined
} from '@ant-design/icons-vue'
import {
  Input as AInput,
  Select as ASelect,
  Popover as APopover,
  Tag as ATag,
  message
} from "ant-design-vue";
import {onMounted, reactive, ref} from "vue";

const ASelectOption = ASelect.Option
const AInputSearch = AInput.Search

const {closeCurrent} = useTabs(router);

const {
  createConfirm
} = useMessage()

const flag = ref('1')

const formItems = ref({
  selectType: 'ecCode',
  selectValue: ''
})
const tableData:any = ref([]);
const tableDataAll:any = ref([]);
const compState = reactive({
  absolute: false,
  loading: false,
  tip: '加载中...',
});
function openCompFullLoading() {
  openLoading(false);
}
function openLoading(absolute: boolean) {
  compState.absolute = absolute;
  compState.loading = true;
}
const columns: any = [
  {
    title: 'ID',
    dataIndex: 'id',
    defaultHidden: true,
    ellipsis: true
  },
  {
    title: '状态',
    dataIndex: 'flag',
    width: '8%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'flag' }

  },
  {
    title: '编码',
    dataIndex: 'ecCode',
    ellipsis: true,
    width: '10%',
    slots: { customRender: 'ecCode' },
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
  },
  {
    title: '类别名称',
    dataIndex: 'ecName',
    ellipsis: true,
    width: '20%',
    customCell: () => {		// 在此处可以修改单元格中的样式
      return {style: {'text-align': 'center', '-webkit-line-clamp': 2}} // return 想要设置的样式
    },
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'ecName' }

  },
  {
    title: '资产属性',
    dataIndex: 'aname',
    width: '10%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'aname' }
  },
  {
    title: '计量单位',
    dataIndex: 'uname',
    width: '6%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'uname' }
  },
  {
    title: '使用年限(月)',
    dataIndex: 'serviceLife',
    width: '6%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'serviceLife' }
  },
  {
    title: '净残值率',
    dataIndex: 'netSalvage',
    width: '6%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'netSalvage' }
  },
  {
    title: '折旧方法',
    dataIndex: 'mname',
    width: '12%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'mname' }
  },

  {
    title: '卡片样式',
    dataIndex: 'cardStyle',
    width: '10%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'cardStyle' }
  },
/*  {
    title: '进项税率',
    dataIndex: 'inputTax',
    width: '6%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'inputTax' }
  },*/
  {
    title: '类型',
    dataIndex: 'zjType',
    width: '8%',
    ellipsis: true,
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: { customRender: 'zjType' }
  },
]
function formatFlag(flag:any) {
  let str = '启用'
  switch (flag) {
    case '1':
      str = '启用'
      break
    case '0':
      str = '停用'
      break
  }
  return str
}

//部门
const deptList:any = ref([])
async function reloadDept() {
  loadMark.value = true
  const res:any = await useRouteApi(getDeptListById,{schemaName: dynamicTenantId})({})
  deptList.value = res.items
  if(deptList.value){
    tableDataAll.value = deptList.value.filter(item=> {
      if (thisCheckKey.value!=null && thisCheckKey.value!='0' && thisCheckKey.value!='undefined') {
        return thisCheckKey.value.indexOf(item.id)!=-1 && item.flag.indexOf(flag.value)!=-1
      }
      return item.flag.indexOf(flag.value)!=-1
    })
    tableData.value = tableDataAll.value
   /* await setPagination({
      total: tableData.value.length
    })*/
  }
  totalData.value = await useRouteApi(getTotalData,{schemaName: dynamicTenantId})({})

  loadMark.value = false
}
function formatParentId(parentId){
  let str = ''
  deptList.value.forEach(
    function (dept:any) {
      if (dept.id == parentId){
        str = dept.deptName
      }
    }
  )
  return str
}


const thisCheckKey = ref('')

function handleSelect(key) {
  if (null != key) {
    thisCheckKey.value = key
    reloadTable()
  }
}

// 这是示例组件
const dynamicTenantId = ref(getCurrentAccountName(true))
const [registerTable, {
  reload,
  getColumns,
  getPaginationRef,
  getDataSource,
  setTableData
}] = useTable({
  /*api: async (params) => {
    return useRouteApi(GetDeptTree, {schemaName: dynamicTenantId})(params)
  },*/
  columns: columns,
  bordered: true,
  showIndexColumn: false,

  actionColumn: {
    width: '8%',
    title: '操作',
    dataIndex: 'action',
    customHeaderCell: () => {		// 表头加粗
      return {style: {'font-weight':'bold'}} // return 想要设置的样式
    },
    slots: {customRender: 'action'}
  },
  searchInfo: {
    id: thisCheckKey.value,
    flag: flag.value
  }
})
const [registerEditPage, { openModal: openEditPage }] = useModal()
const [registerSavePage, { openModal: openSavePage }] = useModal()
const val = {
  id: null,
  parentId: '',
  deptCode: '',
  deptName: '',
  uniqueCodeUser: '',
  createDate: '',
  flag: ''
}
const openAddPage = () => {
  openSavePage(true, {
    data: val,
    dynamicTenantId: dynamicTenantId.value
  })
}
const condClick = (data:any) => {
  /*openEditPage(true, {
    data: data,
    dynamicTenantId: dynamicTenantId.value
  })*/
}

const del = async(data:any) => {
  createConfirm({
    iconType: 'error',
    title: '警告',
    content: '删除后数据将不能恢复，你确认要删除吗?',
    onOk: async() => {
      isShowTree.value = false
      await useRouteApi(deleteDept,{schemaName: dynamicTenantId})(data)
      isShowTree.value = true
      // alert('删除成功！')
      message.success('删除成功！')
      await reloadTable()
    },
    onCancel: () => {
      return false
    }
  })
  // isShowTree.value = false
  // await useRouteApi(deleteDept,{schemaName: dynamicTenantId})(data)
  // isShowTree.value = true
  // // alert('删除成功！')
  // message.success('删除成功！')
  // reloadTable()
}

async function saveData(data:any) {
  isShowTree.value = false
  await useRouteApi(saveDept, {schemaName: dynamicTenantId})(data)
  isShowTree.value = true
  await reloadTable()
  checkRow.value = []
  state.selectedRowKeys = []
  //刷新tree
  //刷新子页面list
  mychild.value.fetch(dynamicTenantId.value)
}
async function editFlagData(data:any) {
  isShowTree.value = false
  await useRouteApi(editFlag,{schemaName: dynamicTenantId})(data)
  isShowTree.value = true
  await reloadTable()
}
async function onSearch(){
  loadMark.value = true
  tableData.value = tableDataAll.value.filter(item => {
    //通过部门编码过滤
    if (formItems.value.selectType == 'ecCode' && formItems.value.selectValue != '') {
      return item.deptCode.indexOf(formItems.value.selectValue) != -1
    }
    //通过部门名称过滤
    if (formItems.value.selectType == 'ecName' && formItems.value.selectValue != '') {
      return item.deptName.indexOf(formItems.value.selectValue) != -1
    }
    return item
  })
  await setPagination({
    total: tableData.value.length
  })
  loadMark.value = false
}

const isShowTree = ref(true)

async function reloadTable() {
  await reloadDept()
  /*reload({
    searchInfo: {
      id: thisCheckKey.value,
      flag: flag.value
    }
  })*/
  checkRow.value = []
  state.selectedRowKeys = []
}

function onChangeSwitch(str) {
  flag.value = str
  reloadTable()
}

//选中内容
const state = reactive<{
  selectedRowKeys: [];
  loading: boolean;
}>({
  selectedRowKeys: [], // Check here to configure the default column
  loading: false,
});
const checkRow: any = ref([])
const onSelectChange = (selectedRowKeys, row) => {
  // console.log('selectedRowKeys changed: ', row);
  state.selectedRowKeys = selectedRowKeys;
  checkRow.value = row
};

const editOpen = () => {
  if (checkRow.value.length == 1) {
    const item = checkRow.value[0]
    // if(item.zjType === '1'){
    //   message.error('不能修改系统预制数据！')
    //   return false
    // } 
    openEditPage(true, {
      data: checkRow.value[0],
      dynamicTenantId: dynamicTenantId.value
    })
  } else {
    createWarningModal({
      iconType: 'warning',
      title: '编辑',
      content: '请选择一条进行编辑！'
    })
  }
}
const mychild =  ref(null)
async function delList() {
  if (checkRow.value.length > 0) {
    for (let i = 0; i < checkRow.value.length; i++) {
      const item = checkRow.value[i]
      if(item.zjType === '1'){
        message.error('不能删除系统预制数据！')
        return false
      }
    }
    createConfirm({
      iconType: 'error',
      title: '警告',
      content: '删除后数据将不能恢复，你确认要删除吗?',
      onOk: async () => {
        isShowTree.value = false
        for (let i = 0; i < checkRow.value.length; i++) {
          const item = checkRow.value[i]
          await useRouteApi(deleteDept, {schemaName: dynamicTenantId})(item)
        }
        isShowTree.value = true
        checkRow.value = []
        state.selectedRowKeys = []
        message.success('删除成功！')
        await reloadTable()
        //刷新tree
        //刷新子页面list
        mychild.value.fetch(dynamicTenantId.value)
      },
      onCancel: () => {
        return false
      }
    })
  } else {
    createWarningModal({
      iconType: 'warning',
      title: '删除',
      content: '请选择需要删除的内容！'
    })
  }
}

import {getThisAdInfoData} from "/@/api/record/system/financial-settings";
import {getCurrentAccountName, getThisIndexImg} from "/@/api/task-api/tast-bus-api";
import {useRouteApi} from "/@/utils/boozsoft/datasource/datasourceUtil";
import {useMessage} from "/@/hooks/web/useMessage";
import AccountPicker from "/@/boozsoft/components/AccountPicker/AccountPicker-GDZC.vue";
import {findDataBase} from "/@/api/record/system/account";
import {psnFindAll} from "/@/api/psn/psn";
import {useTabs} from "/@/hooks/web/useTabs";
import router from "/@/router";
import {aoaToSheetXlsx} from "/@/components/Excel";
import {useCompanyOperateStoreWidthOut} from "/@/store/modules/operate-company";
import {useUserStoreWidthOut} from "/@/store/modules/user";
import {useNewPrint} from "/@/utils/boozsoft/print/print";
import {tableStyle} from "/@/store/modules/abc-print";
const defaultPage = ref(true)
const pageParameter:any = reactive({
  showRulesSize: 'MIN',
  companyCode: '100',
  companyName: '湖北万亚软件技术有限公司',
  ifUnit: false,
  total: 0,
  thisAdInfo: {}
})
function init() {
  getThisAdInfoData({'accId': getCurrentAccountName(false)}).then(res=>{
    if (null != res /*&& res.independent == 0*/) {
      defaultPage.value = false
      pageParameter.companyName = res.accNameCn
      pageParameter.companyCode = res.coCode
      pageParameter.ifUnit = res.icorp == '1'
    }
    initTableWidth()
  })
}

init()
const visible = ref(false);
const windowWidth = (document.documentElement.clientWidth - (70 + 280))
const windowHeight = (window.innerHeight - (300))
const tableRef: any = ref(null)
const totalColumnWidth = ref(0)

function initTableWidth() {
  let total = windowWidth
  if (visible.value && (windowWidth - 260) < total) total -= (total - (windowWidth - 260))
  // totalColumnWidth.value = total
  tableRef.value.$el.style.setProperty('width', (total + 70) + 'px')
}
const accNameAll =  ref(null)
const loadMark = ref(false)
const {createWarningModal} = useMessage();

const totalData = ref('0')
const dynamicAdReload = async (obj) => {
  // const dataBase: any = await findDataBase(obj.accId, obj.year)
  dynamicTenantId.value = obj.accountMode
  accNameAll.value = obj.target.accName
  await reloadDept()
  let data: any = {}
  data.id = thisCheckKey.value
  data.flag = flag.value
  data.page = getPaginationRef().current
  data.size = getPaginationRef().pageSize
  // let res = await useRouteApi(GetDeptTree, {schemaName: dynamicTenantId})(data)
  let res = tableData.value
  // if (res != null && res.length > 0){
  loadMark.value = true
  setTableData([]) // 清空可能残留的数据
  setTableData(res)
    // 底部分页信息
    pageParameter.thisAdInfo = obj
    pageParameter.total = res.length
   // setPagination({total:res.length})
  /*}else {
    createWarningModal({title: '温馨提示',content: '暂无任何数据！'});
    pageParameter.thisAdInfo = {}
    pageParameter.total = -1
  }*/
  loadMark.value = false
}


const exportExcelNow = async () => {
  const data = JSON.parse(JSON.stringify(getDataSource()))
  // debugger
  const arrHeader = ['资产类别编码','资产类别名称','资产属性','计量单位','使用年限(月)','净残值率','折旧方法','卡片样式','类型'];
  const columnList = ['ecCode','ecName','aname','uname','serviceLife','mname','netSalvage','cardStyle','zjType']
  data.forEach(v=>{
    v.zjType = v.zjType === '0' ? '自定义':'系统'
  })
  const arrData = data.map((item) => columnList.map(column=>item[column]));
  // 保证data顺序与header一致
  aoaToSheetXlsx({
    data: arrData,
    header: arrHeader,
    filename: '资产类别.xlsx',
  });
}
//导入Excel
const [registerExcelPage, { openModal: openExcelPage }] = useModal()
const openExcel = () => {
  openExcelPage(true, {
    data: {
      dynamicTenantId: dynamicTenantId.value
    }
  })
}

async function saveExcel(data:any) {
  await useRouteApi(excelDept, {schemaName: dynamicTenantId})(data)
  await reloadDept()
  //刷新tree
  //刷新子页面list
  mychild.value.fetch(dynamicTenantId.value)
}
const [registerPrintPage, {openModal: openPrintPage}] = useModal()
const openPrint = () => {
  openPrintPage(true, {
    data: {
      dynamicTenantId: dynamicTenantId.value,
      defaultAdName: useCompanyOperateStoreWidthOut().getSchemaName,
      year: '2021',
    }
  })
}

const userName = useUserStoreWidthOut().getUserInfo.username
const loadPrint = (obj) => {
  openCompFullLoading()
  const data = JSON.parse(JSON.stringify(getDataSource()))
  let printList: any = []
  let printUser = ''
  if(obj.printUser){
    printUser = '制表人：'+ userName
  }

  let title = '资产类别'
  //格式化数据
  data.forEach((item,index) => {
    let item1 = {}

    item1[0] = item.ecCode
    item1[1] = item.ecName
    item1[2] = item.aname
    item1[3] = item.uname
    item1[4] = item.serviceLife
    item1[5] = item.mname
    item1[6] = item.netSalvage
    item1[7] = item.cardStyle
    item1[8] = item.zjType === '0' ? '自定义':'系统'
    printList.push(item1)
  })
  for (let i=0; i<printList.length%26; i++){
    let item1 = {}
    item1[0] = ''
    item1[1] = ''
    item1[2] = ''
    item1[3] = ''
    item1[4] = ''
    item1[5] = ''
    item1[6] = ''
    item1[7] = ''
    item1[8] = ''
    printList.push(item1)
  }
  let num = Math.ceil(printList.length/26)
  useNewPrint({data: ['l', 'px', 'a4', true]}, (doc) => {
    loadMark.value = false
    doc.autoTable({
      head: [['', '', '', title, '', '', '', '', ''],
        ['核算单位:'+accNameAll.value, '', '', '', '', '', '', '', ''],
        ['资产类别编码','资产类别名称','资产属性','计量单位','使用年限(月)','净残值率','折旧方法','卡片样式','类型']
      ],
      body: printList,
      // startY: 60,
      styles: tableStyle(),
      margin: {
        left: 60,
        top: 20,
      },
      addPageContent: (data) => {
        //data.table.finalY 表格最大可容纳y轴坐标，超出时将根据设置决定是否另取一页.在页面需要分页时出现
        let tabHeigth = data.table.height,
          tabMarginTop = data.settings.margin.top,
          tabSize = data.table.finalY - tabMarginTop,//表格最大Y轴-表格顶部距离得到每页表格的最大值
          tabMarginLeft = data.settings.margin.left;
        if (data.table.finalY)//是否分页 有分页时才有该属性finalY
          if (data.pageNumber != Math.ceil(tabHeigth / tabSize)) return;//如果需要每一页都显示页尾则注释该行代码
        //data.cursor.y ,data.cursor.x:表格最后一个单元坐标
        data.doc.setFontSize(9)
        // data.doc.setFont('fuhuiR', 'bold')
        /*doc.autoTableText(
          '核算单位：' ,
          tabMarginLeft,
          data.cursor.y + 3,
          0
        );*/
        doc.autoTableText(
          printUser,
          data.cursor.x/2-25,
          data.cursor.y + 3,
          0
        );
        doc.autoTableText(
          '第'+data.doc.getCurrentPageInfo().pageNumber+'页/共'+num+'页',
          // '第'+data.doc.getCurrentPageInfo().pageNumber+'页',
          data.cursor.x - 50,
          data.cursor.y + 3,
          0
        );
      },
      didParseCell(data) {
        data.cell.styles.cellPadding = {top: 3, left: 2, right: 2, bottom: 2}
        data.cell.styles.fillColor = [255, 255, 255]
        data.cell.styles.fontSize = 9
        data.cell.styles.lineColor = [150, 150, 150]
        // data.cell.styles.bold = false
        //data.cell.styles.fontStyle = 'bold'

        if (data.section == 'head' && data.row.index == 0) {
          data.cell.styles.fontSize = 20
          data.cell.styles.fontStyle = 'bold'
          data.cell.styles.lineColor = [255, 255, 255]
          if (data.column.index == 3) {
            data.cell.colSpan = 2
            data.cell.styles.halign = 'center'
          }
        }
        if (data.section == 'head' && data.row.index == 1) {
          data.cell.styles.fontSize = 10
          data.cell.styles.fontStyle = 'bold'
          data.cell.styles.lineColor = [255, 255, 255]
          data.cell.colSpan = 3
          data.cell.styles.halign = 'left'
        }
        if (data.section == 'head' && data.row.index == 2) {
          data.cell.styles.fontSize = 10
          data.cell.styles.cellPadding = {top: 4, left: 2, right: 2, bottom: 3}
          data.cell.styles.fontStyle = 'bold'
          data.cell.styles.fillColor = [230, 230, 230]
          data.cell.styles.halign = 'center'
        }
        if (data.section == 'body'){
          if (data.row.index%2==1) {
            data.cell.styles.fillColor = [240,240,240]
          }

        }
      },
      columnStyles: {
        0: {maxHeight: 10,cellWidth: 50, halign: 'left'},
        1: {cellWidth: 80, halign: 'left'},
        2: {cellWidth: 80, halign: 'left'},
        3: {cellWidth: 50, halign: 'center'},
        4: {cellWidth: 50, halign: 'center'},
        5: {cellWidth: 50, halign: 'center'},
        6: {cellWidth: 80, halign: 'left'},
        7: {cellWidth: 50, halign: 'center'},
        8: {cellWidth: 50, halign: 'center'},
      }
    })
    compState.loading = false
  })

}

//js切割字符串
function setString(str, len) {
  var strlen = 0;
  var s = "";
  for (var i = 0; i < str.length; i++) {
    if (str.charCodeAt(i) > 128) {
      strlen += 2;
    } else {
      strlen += 1.2;
    }
    s += str.charAt(i);
    if (strlen >= len) {
      return s+"...";
    }
  }
  return s;
}
//加
function add(a, b) {
  let c, d, e;
  try {
    c = a.toString().split(".")[1].length;
  } catch (f) {
    c = 0;
  }
  try {
    d = b.toString().split(".")[1].length;
  } catch (f) {
    d = 0;
  }
  return e = Math.pow(10, Math.max(c, d)), (a * e + b * e) / e;
}
//减
function sub(a, b) {
  let c, d, e;
  try {
    c = a.toString().split(".")[1].length;
  } catch (f) {
    c = 0;
  }
  try {
    d = b.toString().split(".")[1].length;
  } catch (f) {
    d = 0;
  }
  return e = Math.pow(10, Math.max(c, d)), (a * e - b * e) / e;
}
</script>
<style src="./global-menu-index.less" lang="less" scoped></style>

<style lang='less' scoped >
.a-table-font-size-16 :deep(td),
.a-table-font-size-16 :deep(th) {
  font-size: 16px !important;
  padding: 5px 8px !important;
}
.bg-white{
  width: 250px ;
  min-height: 250px ;
  height: calc(100% - 228px);
  border: 1px #cccccc solid;
  background:white ;
  margin-top: -0.5%;
}
.a-table-font-size-12 :deep(td),
.a-table-font-size-12 :deep(th) {
  font-size: 14px !important;
  padding: 4px 8px !important;
}

.app-container:nth-of-type(1) {
  background-color: #f2f2f2;
  padding: 10px 5px;
  margin: 10px 10px 5px;
}

.app-container:nth-of-type(2) {
  padding: 0px;
  margin: 5px 10px;
  background: #b4c8e3 !important;
}
.app-container-head{
  padding-left: 5px;
}
</style>
