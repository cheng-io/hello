<template>
  <BasicModal
    width="900px"
    v-bind="$attrs"
    title="常用摘要设置导入"
    @ok="handleOk()"
    @register="register"
    okText="开始导入"
    :canFullscreen="false"
  >
    <template #title>
      <div style="display: flex;" class="vben-basic-title">
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
            <span style="font-size: 16px;">导入内容：</span><span style="font-weight: bold;font-size: 16px;">常用凭证设置导入</span><br/><br/>
            <ul style="list-style-type: disc;color:#0096c7;margin-left: 20px;font-size: 24px;">
              <li><span style="font-size: 16px;color: #000000;">全新添加导入</span></li>
            </ul>
          </div>
          <div>
            <Tooltip placement="top" >
              <Button size="small" style="color: #3eadbe">查看帮助</Button>
              <EllipsisOutlined style="cursor: pointer;margin-left: 10%;color: #3eadbe"/>
            </Tooltip>
            <br>
            <br>
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
      </div>
    </div>

    <template #footer>
      <div>
        <Button @click="closeModal()">放弃</Button>
        <Button @click="handleOk()" :disabled="saveClick" type="primary">开始导入</Button>
      </div>
    </template>
<!--    <div class="nc-open-content">
      <div class="open-content-up" style="height: 300px;margin-left: 5%;width:90%;">

        <div
          style="position: relative;background: #ffffff;border: 1px solid #999999;padding: 10px 20px;margin-top: 20px;">
          <div
            style="position: absolute;background: #ffffff; top: -15px;left: 20px;padding: 0 10px;">
            导入说明
          </div>
          <p>1.导入文件格式必须为xls或xlsx，且数据表内容必须放置在第一个页签sheet1中；</p>
          <p>2.常用摘要编码和内容不允许与当前库重复；</p>
        </div>
        <br/><br/>
        <label style="width: 150px;">
          <a @click="exportExcel()">
            <DownloadOutlined/>
            下载导入模板</a>
        </label>

        <div style="margin-left: 40px;margin-top: 30px;">
          <ImpExcel v-if="isActiveImpExcel" @success="loadDataSuccess">
            <a-button class="m-3"> 导入Excel</a-button>
          </ImpExcel>
        </div>
        <br>

      </div>
    </div>-->

  </BasicModal>
</template>

<script setup="props, {content}" lang="ts">
import {nextTick, ref, unref} from 'vue'
import {BasicModal, useModalInner} from '/@/components/Modal'
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
// import {aoaToSheetXlsx} from "/@/views/boozsoft/xian_jin_liu_liang/yin_hang_dui_zhang/yin_hang_dui_zhang_dan/excel/components/importexcel";
const aoaToSheetXlsx=null
import {useRouteApi} from "/@/utils/boozsoft/datasource/datasourceUtil";
import {findAllApi} from "/@/api/boozsoft/account/AccvoucherCdigest";
import {findAllApi as findClassAllApi} from '/@/api/boozsoft/account/AccvoucherCdigestClass'

const {
  createErrorModal
} = useMessage()

const formItems: any = ref({})

const saveClick:any = ref(false)

const excelValue:any = ref(1)

function onChange(e) {
  console.log('radio checked', e.target.value);
}

const emit = defineEmits(['register', 'save'])
const isActiveImpExcel = ref(false)
const list: any = ref([])

function loadDataSuccess(excelDataList) {
  list.value = []
  const items = excelDataList[0].results
  if (items.length > 0) {
    for (let i = 0; i < items.length; i++) {
      const item = items[i]
      const item1: any = {}
      item1.ccode = item['编码']
      item1.content = item['常用摘要内容']
      item1.classCode = item['所属分类编码']
      if (item1.classCode == '' || item1.classCode == null) {
        item1.classCode = '9999'
      } else {
        //部门名称转换为唯一码
        let num = 0
        classList.value.forEach(dept => {
          if (dept.classCode == item1.classCode) {
            item1.classCode = dept.classCode
            num++
          }
        })
        if (num > 0) {
          createErrorModal({
            iconType: 'warning',
            title: '提示',
            content: '第' + (i + 1) + '行分类编码不存在,自动添加分配到“未分配“分类中！'
          })
          item1.classCode = '9999'
        }
      }
      list.value.push(item1)
    }
    for (let i = 0; i < list.value.length; i++) {
      const item1 = list.value[i];
      //判断人员姓名是否为空
      if (item1.ccode == null || item1.ccode == '') {
        // msg='第'+(i+1)+'行人员姓名为空,不能进行人员信息导入'
        createErrorModal({
          iconType: 'warning',
          title: '提示',
          content: '第' + (i + 2) + '行编码为空,不能进行常用摘要信息导入！'
        })
        list.value = []
        return false
      }
      if (item1.content == null || item1.content == '') {
        // msg='第'+(i+1)+'行人员姓名为空,不能进行人员信息导入'
        createErrorModal({
          iconType: 'warning',
          title: '提示',
          content: '第' + (i + 2) + '行常用摘要内容为空,不能进行常用摘要信息导入！'
        })
        list.value = []
        return false
      }
      for (let j = 0; j < list.value.length; j++) {
        const item2 = list.value[j];
        if (i != j) {
          if (item1.ccode != '' && item1.ccode != null && item1.ccode == item2.ccode) {
            createErrorModal({
              iconType: 'warning',
              title: '提示',
              content: '第' + (i+2) + '行编码信息与第' + (j+2) + '行的信息重复，请修改后重新导入！'
            })
            list.value = []
            return false
          }
          if (item1.content != '' && item1.content != null && item1.content == item2.content) {
            createErrorModal({
              iconType: 'warning',
              title: '提示',
              content: '第' + (i+2) + '行常用摘要信息与第' + (j+2) + '行的常用摘要重复，请修改后重新导入！'
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
      content: '未发现导入数据，请检查数据是否在sheet1页签中！'
    })
  }
}

//导入时判断人员姓名不为空
let msg = ''

function checkExcel() {
  msg = ''
  if (list.value.length > 0) {
    for (let i = 0; i < list.value.length; i++) {
      const item = list.value[i];
      //根据导入类型导入数据是否重复
      for (let j = 0; j < accList.value.length; j++) {
        const acc = accList.value[j];
        if (item.ccode != '' && item.ccode != null && acc.ccode == item.ccode) {
          msg = '第' + (i + 2) + '行编码重复,不能进行常用摘要信息导入！'
          return false
        }
        if (item.content != '' && item.content != null && acc.content == item.content) {
          msg = '第' + (i + 2) + '行常用摘要重复,不能进行常用摘要信息导入！'
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

const accList: any = ref([])
const classList: any = ref([])
const dynamicTenantId = ref()
const [register, {closeModal}] = useModalInner((data) => {
  saveClick.value=false
  dynamicTenantId.value = data.dynamicTenantId
  useRouteApi(findAllApi, {schemaName: dynamicTenantId})({}).then(res => {
    accList.value = res.items
  })
  useRouteApi(findClassAllApi, {schemaName: dynamicTenantId})({}).then(res => {
    classList.value = res
  })

  isActiveImpExcel.value = false
  nextTick(() => {
    isActiveImpExcel.value = true
  })
})

async function handleOk() {
  saveClick.value=true
  // formItems.value.excelValue = excelValue.value
  // formItems.value.object = list.value
  // formItems.value.cateCode = cateCode.value
  checkExcel()
  console.log(msg)
  if (msg == '') {
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
  const arrHeader = ['编码', '常用摘要内容', '所属分类编码'];
  console.log(arrHeader)
  // 保证data顺序与header一致
  aoaToSheetXlsx({
    data: [],
    header: arrHeader,
    filename: '常用摘要模板.xlsx',
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
.import-centent-div{
  .import-info-div {
    width: 90%;
    margin-left: 2%;
    height: 180px;
    border-radius: 4px;

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
