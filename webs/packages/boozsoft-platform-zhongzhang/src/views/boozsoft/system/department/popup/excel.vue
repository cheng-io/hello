<template>
  <BasicModal
    width="700px"
    v-bind="$attrs"
    title="人员信息导入"
    @ok="handleOk()"
    @register="register"
    okText="开始导入"
    :loading="loadMark"
    :canFullscreen="false"
  >
    <Loading :loading="compState.loading" :absolute="compState.absolute" :tip="compState.tip" />
    <template #title>
      <div style="height: 30px;width: 100%;background-color: #5f375c;color: white;line-height: 30px;text-align: left;">
        <AppstoreOutlined  style="margin: 0 2px;font-size: 14px;"/>
        <span style="font-size: 14px">数据导入</span>
      </div>
    </template>
    <div style="width: 100%;display: table;">
      <div  style="text-align: right;width: 44%;float: left;">
        <CloudUploadOutlined style="margin: 0 2px;font-size: 40px;color: #0096c7;"/>
      </div>
      <div style="text-align: left;width: 54%;float: right;font-size: 24px;font-weight: bold;color: #0096c7;">
        主单据导入
      </div>
    </div>
    <div style="margin-top: 40px;margin-left: 30px;">
      <span style="font-size: 20px;">导入内容：</span><span style="font-weight: bold;font-size: 20px;">人员信息</span>
      <p/>
      <span style="font-size: 20px;">模板样式：</span>
      <Select
        v-model:value="codeTemplateId"
        placeholder=""
        style="width: 40%;margin-right: 2%;font-weight: bold;font-size: 20px;">
        <template #suffixIcon><CaretDownOutlined style="color:#666666;" /></template>
        <SelectOption v-for="tem in templateList" :key="tem.id" :value="tem.id">
          {{ tem.tname }}
        </SelectOption>
      </Select>
      <span style="font-size: 18px;margin-left: 100px;">
        <DownloadOutlined style="font-size: 30px;"/>
        <a @click="exportExcel()">&emsp;模板下载</a>
      </span>
    </div>
    <Tabs v-model:activeKey="excelValue" style="margin-top: 40px;">
      <TabPane key="1" tab="全新添加导入">
      </TabPane>
      <TabPane key="2" tab="字段覆盖导入">
      </TabPane>
    </Tabs>
    <br/>
    <ImpExcel v-if="isActiveImpExcel" @success="loadDataSuccess">
      <a-button class="m-3"> 导入Excel </a-button>
    </ImpExcel>
    <br/>

    <template #footer>
      <div style="height: 35px">
        <div style="float: left">
          <a-popover title="使用说明" trigger="click">
            <template #content>
              <p>文件中带 * 为必填项</p>
            </template>
            <a-button>使用说明</a-button>
          </a-popover>
        </div>
        <div style="float: right">
          <Button @click="closeModal()">放弃</Button>
          <Button @click="handleOk()" :disabled="saveClick" type="primary">开始导入</Button>
        </div>
      </div>
    </template>

  </BasicModal>
</template>

<script setup="props, {content}" lang="ts">
import {nextTick, reactive,ref, unref} from 'vue'
import { BasicModal, useModalInner } from '/@/components/Modal'
import {ImpExcel} from "/@/views/boozsoft/system/project/excel/components/importexcel";
import {
  Upload as AUpload,
  Spin as ASpin,
  Select,
  Input as AInput,
  Modal as AModal, Badge, Button, Tabs, Radio,Checkbox,Tooltip
} from 'ant-design-vue';
import { DownloadOutlined,CloudUploadOutlined,CaretDownOutlined,EllipsisOutlined,AppstoreOutlined } from '@ant-design/icons-vue';
const AInputSearch=AInput.Search
const SelectOption=Select.Option
const RadioGroup = Radio.Group
const TabPane = Tabs.TabPane
import {useMessage} from "/@/hooks/web/useMessage";
// import {aoaToSheetXlsx} from "/@/views/boozsoft/xian_jin_liu_liang/yin_hang_dui_zhang/yin_hang_dui_zhang_dan/excel/components/importexcel";
const aoaToSheetXlsx=null
import {getPsnList} from "/@/api/record/system/psn";
import {useRouteApi} from "/@/utils/boozsoft/datasource/datasourceUtil";
import {psnTypeFindAll} from "/@/api/psn-type/psn-type";
import {getDeptList} from "/@/api/record/system/dept";
import {findDocumentTypeAll} from "/@/api/record/system/group-document-type";
import { Loading } from '/@/components/Loading';
const {
  createErrorModal
} = useMessage()

const formItems:any = ref({})
const excelValue:any = ref(1)
function onChange(e) {
  console.log('radio checked', e.target.value);
}

const codeTemplateId = ref('1');
const templateList = ref([{tname:'系统简约模板',id:'1'},{tname:'系统标准模板',id:'2'}]);

const saveClick:any = ref(false)

/******************* 弹框加载中 **************************/
const loadingRef = ref(false);
const compState = reactive({
  absolute: false,
  loading: false,
  tip: '处理中...',
});
function openCompFullLoading() {
  openLoading(false);
}
function openLoading(absolute: boolean) {
  compState.absolute = absolute;
  compState.loading = true;
}
/*******************END**************************/

const emit=defineEmits(['register','save'])
const isActiveImpExcel=ref(false)
const list:any = ref([])
function loadDataSuccess(excelDataList) {
  list.value = []
  const items = excelDataList[0].results
  if (items.length>0) {
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      const item1: any = {}
      item1.psnCode = item['人员编码']
      item1.psnName = item['人员姓名']
      item1.psnType = item['属性']
      item1.psnSex = item['性别']
      item1.jobNum = item['工号']
      item1.uniqueCodeDept = item['部门名称']
      item1.uniquePsnType = item['人员类别']
      item1.cellPhoneNum = item['手机']
      item1.psnEmail = item['Email']
      item1.countryId = item['国别']
      item1.province = item['省市区']
      item1.district = item['详细地址']
      item1.psnWechat = item['微信']
      item1.psnQq = item['钉钉']
      item1.documentType = item['证件类型']
      item1.documentCode = item['证件号码']
      item1.psnStation = item['工位']
      item1.birthDate = item['出生日期']
      item1.entryDate = item['入职日期']
      item1.leaveDate = item['离职日期']
      item1.psnBank = item['开户银行']
      item1.bankArea = item['开户地']
      item1.bankAccount = item['银行账户']
      item1.bankNum = item['银行行号']
      item1.flag = '1'
      if (item1.psnSex=='未知的性别'){
        item1.psnSex = '0'
      } else if (item1.psnSex=='男'){
        item1.psnSex = '1'
      } else if (item1.psnSex=='女'){
        item1.psnSex = '2'
      } else if (item1.psnSex=='未说明的性别'){
        item1.psnSex = '9'
      }
      if (item1.psnType=='外部人员'){
        item1.psnType = '2'
      } else {
        item1.psnType = '1'
      }
      /*if (item1.documentType=='居民身份证'){
        item1.documentType = '1'
      } else if (item1.documentType=='港澳居民来往内地通行证'){
        item1.documentType = '2'
      } else if (item1.documentType=='台湾居民来往大陆通行证'){
        item1.documentType = '3'
      } else if (item1.documentType=='中国护照'){
        item1.documentType = '4'
      } else if (item1.documentType=='外国护照'){
        item1.documentType = '5'
      }*/
      if (item1.documentType=='' || item1.documentType == null){
        item1.documentType = ''
      } else {
        let num = 0
        documentTypeList.value.forEach(document => {
          if (document.cname == item1.documentType) {
            item1.documentType = document.ccode
            num++
          }
        })
        if (num == 0) {
          item1.documentType = ''
        }
      }
      if (item1.uniqueCodeDept==''||item1.uniqueCodeDept==null){
        item1.uniqueCodeDept = '9999'
      } else {
        //部门名称转换为唯一码
        let num = 0
        deptList.value.forEach(dept => {
          if (dept.deptName == item1.uniqueCodeDept) {
            item1.uniqueCodeDept = dept.uniqueCode
            num++
          }
        })
        if (num == 0) {
          /*createErrorModal({
            iconType: 'warning',
            title: '提示',
            content: '第' + (i + 1) + '行部门名称不存在,自动添加分配到“未分配“部门中'
          })*/
          item1.uniqueCodeDept = '9999'
        }
      }
      if (item1.uniquePsnType==''||item1.uniquePsnType==null){
        item1.uniquePsnType = '9999'
      } else {
        //人员类别名称转换为唯一码
        let num = 0
        psnTypeList.value.forEach(psnType => {
          if (psnType.psnTypeName == item1.uniquePsnType) {
            item1.uniquePsnType = psnType.uniqueCode
            num++
          }
        })
        if (num == 0) {
          /*createErrorModal({
            iconType: 'warning',
            title: '提示',
            content: '第' + (i + 1) + '行人员类别名称不存在,自动添加分配到“未分配“类别中'
          })*/
          item1.uniquePsnType = '9999'
        }
      }
      list.value.push(item1)
    }
    for (let i=0; i<list.value.length; i++) {
      const item1 = list.value[i];
      //判断人员姓名是否为空
      if (item1.psnName==null || item1.psnName==''){
        // msg='第'+(i+1)+'行人员姓名为空,不能进行人员信息导入'
        createErrorModal({
          iconType: 'warning',
          title: '提示',
          content: '第'+(i+1)+'行人员姓名为空,不能进行人员信息导入'
        })
        list.value = []
        return false
      }
      if (item1.countryId==null || item1.countryId==''){
        item1.countryId='中国'
      }
      if (item1.psnSex==null || item1.psnSex==''){
        item1.psnSex='0'
      }
      if (item1.psnType==null || item1.psnType==''){
        item1.psnType='1'
      }
      for (let j=0; j<list.value.length; j++) {
        const item2 = list.value[j];
        if (i!=j){
          if (item1.psnCode != '' && item1.psnCode != null && item1.psnCode == item2.psnCode) {
            createErrorModal({
              iconType: 'warning',
              title: '提示',
              content: '第' + (i+2) + '行人员的编码信息与第' + (j+2) + '行的信息重复，请修改后重新导入！'
            })
            list.value = []
            return false
          }
          if (item1.cellPhoneNum != '' && item1.cellPhoneNum != null && item1.cellPhoneNum == item2.cellPhoneNum) {
            createErrorModal({
              iconType: 'warning',
              title: '提示',
              content: '第' + (i+2) + '行人员的手机号码信息与第' + (j+2) + '行的信息重复，请修改后重新导入！'
            })
            list.value = []
            return false
          }
          if (item1.psnWechat != '' && item1.psnWechat != null && item1.psnWechat == item2.psnWechat) {
            createErrorModal({
              iconType: 'warning',
              title: '提示',
              content: '第' + (i+2) + '行人员的微信信息与第' + (j+2) + '行的信息重复，请修改后重新导入！'
            })
            list.value = []
            return false
          }
          if (item1.psnQq != '' && item1.psnQq != null && item1.psnQq == item2.psnQq) {
            createErrorModal({
              iconType: 'warning',
              title: '提示',
              content: '第' + (i+2) + '行人员的钉钉信息与第' + (j+2) + '行的信息重复，请修改后重新导入！'
            })
            list.value = []
            return false
          }
          if (item1.documentCode != '' && item1.documentCode != null && item1.documentCode == item2.documentCode) {
            createErrorModal({
              iconType: 'warning',
              title: '提示',
              content: '第' + (i+2) + '行人员的证件号码信息与第' + (j+2) + '行的信息重复，请修改后重新导入！'
            })
            list.value = []
            return false
          }
        }
      }
    }
  } else {
    createErrorModal({
      iconType: 'warning',
      title: '提示',
      content: '未发现导入数据，请检查数据是否在sheet1页签中'
    })
  }
}
//导入时判断人员姓名不为空
let msg=''
async function checkExcel(){
  msg=''
  if (list.value.length>0) {
    for (let i = 0; i < list.value.length; i++) {
      const item = list.value[i];
      /*//判断人员姓名是否为空
    const psnName = item.psnName
    if (psnName==null || psnName==''){
      msg='第'+(i+1)+'行人员姓名为空,不能进行人员信息导入'
      return false
    }
    const psnCode = item.psnCode
    if (item.countryId==null || item.countryId==''){
      item.countryId='中国'
    }
    if (item.psnSex==null || item.psnSex==''){
      item.psnSex='1'
    }
    if (item.psnType==null || item.psnType==''){
      item.psnType='1'
    }*/
      //根据导入类型导入数据是否重复
      if (excelValue.value == '1') {
        //导入全新人员信息
        for (let j = 0; j < psnList.value.length; j++) {
          const psn = psnList.value[j];
          if (item.psnCode != '' && item.psnCode != null && psn.psnCode == item.psnCode) {
            msg = '第' + (i + 2) + '行人员编码重复,不能进行人员信息导入'
            return false
          }
          if (item.cellPhoneNum != '' && item.cellPhoneNum != null && psn.cellPhoneNum == item.cellPhoneNum) {
            msg = '第' + (i + 2) + '行手机号码重复,不能进行人员信息导入'
            return false
          }
          if (item.psnWechat != '' && item.psnWechat != null && psn.psnWechat == item.psnWechat) {
            msg = '第' + (i + 2) + '行微信重复,不能进行人员信息导入'
            return false
          }
          if (item.psnQq != '' && item.psnQq != null && psn.psnQq == item.psnQq) {
            msg = '第' + (i + 2) + '行钉钉重复,不能进行人员信息导入'
            return false
          }
          if (item.documentCode != '' && item.documentCode != null && psn.documentCode == item.documentCode) {
            msg = '第' + (i + 2) + '行证件号码重复,不能进行人员信息导入'
            return false
          }
        }
      } else {
        //对已存在人员进行辅加字段信息导入
        for (let j = 0; j < psnList.value.length; j++) {
          const psn = psnList.value[j];
          if (psn.psnCode == item.psnCode) {
            item.id = psn.id
            item.uniqueCode = psn.uniqueCode
          }
          if (psn.psnCode != item.psnCode) {
            if (item.cellPhoneNum != '' && item.cellPhoneNum != null && psn.cellPhoneNum == item.cellPhoneNum) {
              msg = '第' + (i + 2) + '行手机号码重复,不能进行人员信息导入'
              return false
            }
            if (item.psnWechat != '' && item.psnWechat != null && psn.psnWechat == item.psnWechat) {
              msg = '第' + (i + 2) + '行微信重复,不能进行人员信息导入'
              return false
            }
            if (item.psnQq != '' && item.psnQq != null && psn.psnQq == item.psnQq) {
              msg = '第' + (i + 2) + '行钉钉重复,不能进行人员信息导入'
              return false
            }
            if (item.documentCode != '' && item.documentCode != null && psn.documentCode == item.documentCode) {
              msg = '第' + (i + 2) + '行证件号码重复,不能进行人员信息导入'
              return false
            }
          }
        }
        const id = item.id
        if (id == null || id == '') {
          msg = '第' + (i + 2) + '行导入的字段对应人员信息不存在,不能进行附加字段信息导入'
          return false
        }
      }
    }
    return true
  } else {
    msg = '请选择您需要导入的文件!'
    return false
  }
}

const psnList:any = ref([])
const deptList:any = ref([])
const psnTypeList:any = ref([])
const documentTypeList:any = ref([])
const dynamicTenantId = ref()
const loadMark = ref(false)
const [register, { closeModal }] = useModalInner(async(data) => {
  saveClick.value=false
  loadMark.value = true
  dynamicTenantId.value = data.dynamicTenantId
  psnList.value = await useRouteApi(getPsnList,{schemaName: dynamicTenantId})({})
  const res = await useRouteApi(psnTypeFindAll,{schemaName: dynamicTenantId})({})
  psnTypeList.value = res.items
  const res1 = await useRouteApi(getDeptList,{schemaName: dynamicTenantId})({})
  deptList.value = res1.items

  //证件类型
  documentTypeList.value = await findDocumentTypeAll()

  isActiveImpExcel.value=false
  nextTick(()=>{
    isActiveImpExcel.value=true
  })
  loadMark.value = false
})
async function handleOk() {
  // formItems.value.excelValue = excelValue.value
  // formItems.value.object = list.value
  // formItems.value.cateCode = cateCode.value
  saveClick.value=true
  await checkExcel()
  console.log(msg)
  if (msg=='') {
    emit('save', unref(list))
    closeModal()
    saveClick.value=false
    return true
  } else {
    createErrorModal({
      iconType: 'warning',
      title: '导入失败',
      content: msg
    })
    saveClick.value=false
    return false
  }
}

//下载导入模板
function exportExcel() {
  const arrHeader = ['人员编码', '人员姓名', '属性', '性别', '工号', '部门名称', '人员类别',
    '手机', 'Email', '国别', '省市区', '详细地址', '微信', '钉钉', '证件类型', '证件号码',
    '工位', '出生日期', '入职日期', '离职日期', '开户银行', '开户地', '银行账户', '银行行号'];
  // console.log(arrHeader)
  // 保证data顺序与header一致
  aoaToSheetXlsx({
    data: [],
    header: arrHeader,
    filename: '人员信息模板.xlsx',
  });
}

// 下划线转换驼峰
function toHump(name: any) {
  return name.replace(/\_(\w)/g, function (all: any, letter: any) {
    return letter.toUpperCase();
  });
}

//判断是否整数
function isInteger(obj) {
  return obj % 1 === 0
}
</script>
<style lang="less" scoped>
@import '/@/assets/styles/redTitle-open.less';
:deep(.ant-select-selector) {
  border: none !important;
  border-bottom: 1px solid #bdb9b9 !important;
}
</style>
