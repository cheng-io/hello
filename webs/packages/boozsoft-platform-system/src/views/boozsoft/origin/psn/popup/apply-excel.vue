<template>
  <BasicModal
    width="800px"
    v-bind="$attrs"
    title="人员信息导入"
    @ok="handleOk()"
    @register="register"
    okText="开始导入"
    :loading="loadMark"
    :canFullscreen="false"
  >
    <template #title>
      <div style="display: flex;margin-top: 10px;" class="vben-basic-title">
        <span style="line-height:40px;font-size: 28px;">
          <CloudUploadOutlined style="font-size: 34px;font-weight: bold"/>&nbsp;&nbsp;数据导入
        </span>
      </div>
    </template>
    <div class="import-centent-div" >
      <div class="import-info-div">
        <div class="import-div-top">
          <div>
          </div>
          <div>
            <span style="font-size: 16px;">导入内容：</span><span style="font-weight: bold;font-size: 16px;">人员信息导入</span><br/><br/>
            <span style="font-size: 16px;">模板样式：</span>
            <Select
              v-model:value="codeTemplateId"
              placeholder=""
              style="width: 50%;margin-right: 2%;font-size: 16px;font-weight: bold;">
              <template #suffixIcon><CaretDownOutlined style="color:#666666;" /></template>
              <SelectOption v-for="tem in templateList" :key="tem.id" :value="tem.id">
                {{ tem.tname }}
              </SelectOption>
            </Select><br/><br/>
            <span style="font-size: 16px;">编码方式：</span>
            <Select
              v-model:value="isAutoCode"
              placeholder=""
              style="width: 50%;margin-right: 2%;font-size: 16px;font-weight: bold;">
              <template #suffixIcon><CaretDownOutlined style="color:#666666;" /></template>
              <SelectOption value="1">自动编码</SelectOption>
              <SelectOption value="2">手动编码</SelectOption>
            </Select>
            <!--            <ul style="list-style-type: disc;color:#0096c7;margin-left: 20px;font-size: 24px;">
                          <li><span style="font-size: 16px;color: #000000;">全新人员导入</span></li>
                          <li><span style="font-size: 16px;color: #000000;">人员附加信息导入</span></li>
                        </ul>-->
          </div>
          <div>
            <Tooltip placement="top" >
              <Button size="small" style="color: #3eadbe">查看帮助</Button>
              <EllipsisOutlined style="cursor: pointer;margin-left: 10%;color: #3eadbe"/>
            </Tooltip>
            <br/>
            <br/>
            <Tooltip placement="top" >
              <DownloadOutlined style="font-size: 30px;"/>
              <a @click="exportExcel()">&emsp;模板下载</a>
            </Tooltip>
          </div>
        </div>
      </div>
      <div class="import-div-bottom">
        <Tabs v-model:activeKey="excelValue">
          <TabPane key="1" tab="全新添加导入">
          </TabPane>
          <TabPane key="2" :disabled="true" tab="字段覆盖导入">
          </TabPane>
        </Tabs>
        <br/>
        <ImpExcel v-if="isActiveImpExcel" @success="loadDataSuccess">
          <a-button class="m-3"> 导入Excel </a-button>
        </ImpExcel>
        <br/>
      </div>
    </div>

    <template #footer>
      <div>
        <Button @click="closeModal()">放弃</Button>
        <Button @click="handleOk()" :disabled="saveClick" type="primary">开始导入</Button>
      </div>
    </template>

  </BasicModal>
</template>

<script setup="props, {content}" lang="ts">
import {nextTick, ref, unref} from 'vue'
import { BasicModal, useModalInner } from '/@/components/Modal'
import {ImpExcel} from "/@/views/boozsoft/system/project/excel/components/importexcel";
import {
  Upload as AUpload,
  Spin as ASpin,
  Select,
  Input as AInput,
  Modal as AModal, Badge, Button, Tabs, Radio,Checkbox,Tooltip
} from 'ant-design-vue';
import { DownloadOutlined,CloudUploadOutlined,CaretDownOutlined,EllipsisOutlined } from '@ant-design/icons-vue';
const AInputSearch=AInput.Search
const SelectOption=Select.Option
const RadioGroup = Radio.Group
const TabPane = Tabs.TabPane
import {useMessage} from "/@/hooks/web/useMessage";
//import {aoaToSheetXlsx} from "/@/views/boozsoft/xian_jin_liu_liang/yin_hang_dui_zhang/yin_hang_dui_zhang_dan/excel/components/importexcel";
const aoaToSheetXlsx=null
import {
  excelPsn,
  findBukongPsnCode,
  findMaxPsnCode,
  getPsnList
} from "/@/api/record/system/group-psn";
import {psnTypeFindAll} from "/@/api/record/system/group-psn-type";
import {findDocumentTypeAll} from "/@/api/record/system/group-document-type";
import {getCodingRule} from "/@/api/record/system/customer_group";
//import {mul} from "/@/views/boozsoft/gdzc/gu-ding-zi-chan-add/calculation";
const mul=null
import {findByUserIdAndOriginId} from "/@/api/record/system/group-permission";
import {useUserStoreWidthOut} from "/@/store/modules/user";
import {excelPsn as excelOriginPsn} from "/@/api/record/system/origin-psn";
import {saveList} from "/@/api/record/system/group-psn-account";
import {load} from "/@/api/group/FileEncodingRules";
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
const isAutoCode = ref('1')

const saveClick:any = ref(false)

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
      item1.psnName = item['人员名称']
      item1.psnType = item['属性']
      item1.psnSex = item['性别']
      item1.jobNum = item['工号']
      // item1.uniqueCodeDept = item['部门名称']
      item1.uniquePsnType = item['人员类别名称']
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
      if (item1.uniqueCodeDept == '' || item1.uniqueCodeDept == null) {
        item1.uniqueCodeDept = '9999'
      }
      if (item1.uniquePsnType == '' || item1.uniquePsnType == null) {
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
        if (num > 0) {
          createErrorModal({
            iconType: 'warning',
            title: '提示',
            content: '第' + (i + 2) + '行人员类别名称不存在,自动添加分配到“未分配“类别中'
          })
          item1.uniquePsnType = '9999'
        }
      }
      list.value.push(item1)
    }
    for (let i=0; i<list.value.length; i++) {
      const item1 = list.value[i];
      //判断人员编码是否为空
      if (isAutoCode.value!='1' || excelValue.value != '1') {
        if (item1.psnCode == null || item1.psnCode == '') {
          createErrorModal({
            iconType: 'warning',
            title: '提示',
            content: '第' + (i + 2) + '行人员编码为空,不能进行人员信息导入'
          })
          list.value = []
          return false
        }
      }
      //判断人员姓名是否为空
      if (item1.psnName==null || item1.psnName==''){
        // msg='第'+(i+1)+'行人员姓名为空,不能进行人员信息导入'
        createErrorModal({
          iconType: 'warning',
          title: '提示',
          content: '第' + (i + 2) + '行人员姓名为空,不能进行人员信息导入'
        })
        list.value = []
        return false
      }
      if (item1.countryId==null || item1.countryId==''){
        item1.countryId='中国'
      }
      if (item1.psnSex==null || item1.psnSex==''){
        item1.psnSex='1'
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
              content: '第' + (i + 2) + '行人员的编码信息与第' + (j + 2) + '行的信息重复，请修改后重新导入！'
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
    await getMaxCode()
    for (let i = 0; i < list.value.length; i++) {
      const item = list.value[i];
      //判断是否为自动编码
      if (isAutoCode.value=='1' && excelValue.value == '1') {
        item.psnCode = psnCode.value.substring(0, qianzhui.value) + '' +
          pad(add(psnCode.value.substring(qianzhui.value, add(qianzhui.value, serialNumLength.value)), i), serialNumLength.value)
      }
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

const psnList: any = ref([])
const psnTypeList: any = ref([])
const documentTypeList: any = ref([])
const loadMark = ref(false)
const originId = ref('')
const isAuto = ref('0')
const [register, {closeModal}] = useModalInner(async(data) => {
  saveClick.value=false
  loadMark.value = true
  originId.value = data.originId
  const permissionList = await findByUserIdAndOriginId(useUserStoreWidthOut().getUserInfo.id,data.originId,'psn')
  if (permissionList.length>0){
    isAuto.value = permissionList[0].isAuto
  } else {
    isAuto.value = '0'
  }
  psnList.value = await getPsnList()
  const psnTypeList1 = await psnTypeFindAll()
  psnTypeList.value = psnTypeList1.items

  //证件类型
  documentTypeList.value = await findDocumentTypeAll()

  isActiveImpExcel.value = false
  nextTick(() => {
    isActiveImpExcel.value = true
  })
  loadMark.value = false
})
async function handleOk() {
  saveClick.value=true
  // formItems.value.excelValue = excelValue.value
  // formItems.value.object = list.value
  // formItems.value.cateCode = cateCode.value
  await checkExcel()
  console.log(msg)
  if (msg=='') {
    loadMark.value = true
    let dateTime = new_Date()
    list.value.map(item=>{
      item.successState=isAuto.value
      item.ctype = '1'
      item.originId = originId.value
      item.applyUser = useUserStoreWidthOut().getUserInfo.realName
      item.applyDate = dateTime
      return item
    })
    //是否自动生效
    //不自动生效往集团表加入一条记录
    if (isAuto.value=='0'){
      await excelPsn(list.value)
    } else {
      //自动生效
      //往集团表插入数据
      await excelPsn(list.value)
      //分配表插入数据
      let list1:any = []
      list.value.forEach(item=>{
        const item1 = {
          id: null,
          uniqueCode: item.uniqueCode,
          ctype: '1',
          originId: item.originId,
          assignUser: useUserStoreWidthOut().getUserInfo.id,
          assignDate: dateTime,
          flag: '1',
          acceptUser: useUserStoreWidthOut().getUserInfo.id,
          acceptDate: dateTime,
        }
        list1.push(item1)
      })
      await saveList(list1)
      //组织表插入数据
      await excelOriginPsn(list.value)
    }
    emit('save', unref(list))
    closeModal()
    loadMark.value = false
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

//获取当年月日
function new_Date() {
  let dateTime
  let yy = new Date().getFullYear()
  let mm = new Date().getMonth() + 1 < 10 ? '0' + (new Date().getMonth()+ 1) : (new Date().getMonth()+ 1)
  let dd = new Date().getDate() < 10 ? '0' + new Date().getDate() : new Date().getDate()
  let hh = new Date().getHours() < 10 ? '0' + new Date().getHours() : new Date().getHours()
  let mf = new Date().getMinutes() < 10 ? '0' + new Date().getMinutes() : new Date().getMinutes()
  let ss = new Date().getSeconds() < 10 ? '0' + new Date().getSeconds() : new Date().getSeconds()
  dateTime = yy + '-' + mm + '-' + dd + ' ' + hh + ':' + mf + ':' + ss
  // console.log(dateTime)
  return dateTime
}

//下载导入模板
function exportExcel() {
  const arrHeader = ['人员编码', '人员名称', '属性', '性别', '工号', '人员类别名称',
    '手机', 'Email', '国别', '省市区', '详细地址', '微信', '钉钉', '证件类型', '证件号码',
    '工位', '出生日期', '入职日期', '离职日期', '开户银行', '开户地', '银行账户', '银行行号'];
  console.log(arrHeader)
  // 保证data顺序与header一致
  aoaToSheetXlsx({
    data: [],
    header: arrHeader,
    filename: '人员信息模板.xlsx',
  });
}

// 下划线转换驼峰
function toHump(name:any) {
  return name.replace(/\_(\w)/g, function (all: any, letter: any) {
    return letter.toUpperCase();
  });
}

//判断是否整数
function isInteger(obj) {
  return obj % 1 === 0
}

//根据编码规则获取编码
const serialNumLength = ref(0)
const qianzhui = ref(0)
const psnCode = ref('')
async function getMaxCode(){
  // 读取编码规则-人员
  let str = ""
  let temp= await load('1-1')
  if(temp.id==null){
    return false
  }
  serialNumLength.value = temp.serialNumLength
  // console.log(temp)
  // console.log(temp.isManual)
  if(temp.prefixOneIs=='true'){
    // console.log(temp.prefixOne)
    if (temp.prefixOne=='1-0'){
      //人员类别
      let psnTypeCode = ''
      psnTypeList.value.forEach(item=>{
        if (item.uniqueCode==formItems.value.uniquePsnType){
          psnTypeCode = item.psnTypeCode.length >= temp.prefixOneLength?
            item.psnTypeCode.substring(0,temp.prefixOneLength) : pad(item.psnTypeCode,temp.prefixOneLength)
        }
      })
      str = str + psnTypeCode
    }
    if (temp.prefixOne=='88'){
      //日期(年月)
      let yy = new Date().getFullYear()
      let mm = new Date().getMonth() + 1 < 10 ? '0' + (new Date().getMonth()+ 1) : (new Date().getMonth()+ 1)
      str = str + yy + mm
    }
    if (temp.prefixOne=='99'){
      //手动录入
      str = str + pad(temp.prefixOneCustomize,temp.prefixOneLength)
    }
    //分隔符
    if (temp.delimiter=='2'){
      str = str + '.'
    } else if (temp.delimiter=='3'){
      str = str + '-'
    }
  }
  if(temp.prefixTwoIs=='true'){
    // console.log(temp.prefixTwo)
    if (temp.prefixTwo=='1-0'){
      //人员类别
      let psnTypeCode = ''
      psnTypeList.value.forEach(item=>{
        if (item.uniqueCode==formItems.value.uniquePsnType){
          psnTypeCode = item.psnTypeCode.length >= temp.prefixTwoLength?
            item.psnTypeCode.substring(0,temp.prefixTwoLength) : pad(item.psnTypeCode,temp.prefixTwoLength)
        }
      })
      str = str + psnTypeCode
    }
    if (temp.prefixTwo=='88'){
      //日期(年月)
      let yy = new Date().getFullYear()
      let mm = new Date().getMonth() + 1 < 10 ? '0' + (new Date().getMonth()+ 1) : (new Date().getMonth()+ 1)
      str = str + yy + mm
    }
    if (temp.prefixTwo=='99'){
      //手动录入
      str = str + pad(temp.prefixTwoCustomize,temp.prefixTwoLength)
    }
    //分隔符
    if (temp.delimiter=='2'){
      str = str + '.'
    } else if (temp.delimiter=='3'){
      str = str + '-'
    }
  }
  if(temp.prefixThreeIs=='true'){
    // console.log(temp.prefixThree)
    if (temp.prefixThree=='1-0'){
      //人员类别
      let psnTypeCode = ''
      psnTypeList.value.forEach(item=>{
        if (item.uniqueCode==formItems.value.uniquePsnType){
          psnTypeCode = item.psnTypeCode.length >= temp.prefixThreeLength?
            item.psnTypeCode.substring(0,temp.prefixThreeLength) : pad(item.psnTypeCode,temp.prefixThreeLength)
        }
      })
      str = str + psnTypeCode
    }
    if (temp.prefixThree=='88'){
      //日期(年月)
      let yy = new Date().getFullYear()
      let mm = new Date().getMonth() + 1 < 10 ? '0' + (new Date().getMonth()+ 1) : (new Date().getMonth()+ 1)
      str = str + yy + mm
    }
    if (temp.prefixThree=='99'){
      //手动录入
      str = str + pad(temp.prefixThreeCustomize,temp.prefixThreeLength)
    }
    //分隔符
    if (temp.delimiter=='2'){
      str = str + '.'
    } else if (temp.delimiter=='3'){
      str = str + '-'
    }
  }
  qianzhui.value = str.length
  // console.log(temp.autoNum)
  if(temp.autoNum=='false') {
    const psn = await findBukongPsnCode(temp.serialNumLength,add(str.length,temp.serialNumLength),str.length,str)
    if (psn.psnCode != null && psn.psnCode != '') {
      str = str + pad(psn.psnCode, temp.serialNumLength)
    } else {
      str = str + pad(temp.serialNumStr, temp.serialNumLength)
    }
  } else {
    const psn = await findMaxPsnCode(temp.serialNumLength,add(str.length,temp.serialNumLength),str.length,str)
    if (psn.psnCode != null && psn.psnCode != '') {
      str = str + pad(add(psn.psnCode,1), temp.serialNumLength)
    } else {
      str = str + pad(temp.serialNumStr, temp.serialNumLength)
    }
  }
  psnCode.value = str
}

/**
 * 字符串前补0
 * @param num
 * @param n
 */
function pad(num, n) {
  let len = num.toString().length;
  while(len < n) {
    num = "0" + num;
    len++;
  }
  return num;
}

/**
 * 加法
 * @param a
 * @param b
 */
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
  return e = Math.pow(10, Math.max(c, d)), (mul(a, e) + mul(b, e)) / e;
}

</script>
<style lang="less" scoped>
.vben-basic-title {
  color: #0096c7 !important;
  border:none !important;
}
.import-centent-div{
  .import-info-div {
    width: 90%;
    margin-left: 2%;
    height: 180px;
    border-radius: 4px;
    margin-top: 30px;

    .import-div-top {
      width: 100%;
      height: 70%;
      display: inline-flex;
      justify-content: space-between;

      > div:nth-of-type(1) {
        width: 25%;
        height: auto;
        background-image: url(/nc/download2.png);
        background-size: 80% 80%;
        background-repeat: no-repeat;
        background-position: 12px;
      }

      > div:nth-of-type(2) {
        width: 55%;
        height: auto;
        padding: 18px 2%;

        > span {
          color: black;
          font-size: 20px;
        }

        > span:nth-of-type(1) {
          font-size: 20px;
        }
      }

      > div:nth-of-type(3) {
        width: 20%;
        height: auto;
        padding: 3.5% 0;
      }

    }
  }
  .import-div-bottom {
    margin-left: 2%;
    width: 96%;
    height: 30%;
  }
  .import-download-div{
    width: 100%;height: 60px;display: inline-flex; justify-content: center; line-height: 50px;
    .download-div {
      width: 50px;
      height: 50px;
      display: block;
      background-color: #6a6a6a;
      border-radius: 50%;
      padding: 2px 10px;
      font-size: 30px;
      color: white;
      cursor: pointer;
    }

    .download-div:hover {
      color: #0096c7;
      background-color: #b4b4b4;
    }
  }
}
:deep(.ant-select-selector) {
  border: none !important;
  border-bottom: 1px solid #bdb9b9 !important;
}
</style>
