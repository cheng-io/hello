<template>
  <BasicModal
    width="700px"
    v-bind="$attrs"
    title="启用列表"
    @ok="handleOk()"
    @register="register"
    :canFullscreen="false"
  >
    <template #title>
      <div style="display: flex;" class="vben-basic-title">
        <span style="line-height:40px;font-size: 28px;">
          <SafetyOutlined style="font-size: 34px;font-weight: bold"/>&nbsp;&nbsp;启用列表
        </span>
      </div>
    </template>
    <div class="nc-open-content">
      <div class="open-content-up">
        <BasicTable
          :row-selection="{ type: 'checkbox', selectedRowKeys: state.selectedRowKeys, onChange: onSelectChange }"
          @row-click="condClick"
          :scroll="{ y: 400 }"
          @register="registerTable"
          class="tables"
          :data-source="tableData"
        >
          <template #shuxing="{record}">
            <span v-if="record.shuxing=='1'">文本</span>
            <span v-if="record.shuxing=='2'">日期</span>
            <span v-if="record.shuxing=='3'">整数</span>
            <span v-if="record.shuxing=='4'">小数</span>
            <span v-if="record.shuxing=='5'">是否</span>
          </template>
          <template #model="{ record }">
            <span v-if="record.scope=='1'">通用</span>
            <span v-if="record.scope=='0' && record.model=='ZW'">总账</span>
            <span v-if="record.scope=='0' && record.model=='FA'">固定资产</span>
            <span v-if="record.scope=='0' && record.model=='STOCK'">存货</span>
          </template>
        </BasicTable>
      </div>
    </div>

  </BasicModal>
</template>

<script setup="props, {content}" lang="ts">
import {nextTick, onMounted, reactive, ref, unref} from 'vue'
import {BasicModal, useModalInner} from '/@/components/Modal'
import {Select as ASelect, Input as AInput, Checkbox as ACheckbox} from 'ant-design-vue'
import {SafetyOutlined} from '@ant-design/icons-vue'
const AInputSearch = AInput.Search
const ASelectOption = ASelect.Option
import {useMessage} from "/@/hooks/web/useMessage";
import {BasicTable, useTable} from "/@/components/Table";
import {findAllDefinedFileList} from "/@/api/record/system/origin-defined-file";

const {
  createErrorModal
} = useMessage()

const emit = defineEmits(['register', 'save'])

const formItems: any = ref({})
const accountList: any = ref([])
const tableData = ref([])
const tableAllData:any = ref([])
const originId:any = ref('')
const [register, {closeModal}] = useModalInner(async (data) => {
  originId.value = data.originId
  tableAllData.value = await findAllDefinedFileList(originId.value)
  tableData.value = tableAllData.value.filter(item=>item.flag=='0')
  await setPagination({
    total: tableData.value.length
  })

  formItems.value.flag = '0'
  state.selectedRowKeys = []
  checkRow.value = []
  flagChange()
})

const columns = [
  {
    title: '编码',
    dataIndex: 'definedCode',
    width: 100,
    ellipsis: true,
  },
  {
    title: '名称',
    dataIndex: 'definedName',
    ellipsis: true,
  },
  {
    title: '类型',
    dataIndex: 'shuxing',
    width: 100,
    ellipsis: true,
    slots: {customRender: 'shuxing'}
  },
  {
    title: '功能模块',
    dataIndex: 'model',
    width: 100,
    ellipsis: true,
    align: 'left',
    slots: { customRender: 'model' }
  },
]
// 这是示例组件
const [registerTable, {reload,setPagination}] = useTable({
  columns: columns,
  bordered: true,
  showIndexColumn: false,
  pagination: false,
  searchInfo: {
    flag: '0',
  }
})

onMounted(async () => {
})

const condClick = () => {

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
  checkRow.value = []
  state.selectedRowKeys = selectedRowKeys;
  checkRow.value = row
};

function flagChange() {
  setTimeout(() => {
    reload({
      searchInfo: {
        flag: formItems.value.flag
      }
    })
  }, 1000)
}

async function handleOk() {
  if (checkRow.value.length > 0) {
    emit('save', unref(checkRow.value))
    closeModal()
    return true
  } else {
    createErrorModal({
      iconType: 'warning',
      title: '启用',
      content: '请至少选择一条记录进行引用！'
    })
    return false
  }
}

</script>
<style lang="less">
.vben-basic-title {
  color: #0096c7 !important;
  border:none !important;
}

.ant-modal-body {
  padding: 0px;
  border-bottom: 1px solid rgb(1, 129, 226);
  border-left: none;
  border-right: none;
  border-top: none !important;
}
.ant-modal-header{
  border: none !important;
}

.nc-open-content {
  input:not(.ant-select-selection-search-input,.ant-input){
    width: 50%;
    border: none !important;
    border-bottom: 1px solid #bdb9b9 !important;
  }

  .ant-input:focus {
    box-shadow: none;
  }

  .ant-select-selector {
    border: none !important;
    border-bottom: 1px solid #bdb9b9 !important;
  }

  label {
    text-align: left;
    width: 110px;
    display: inline-block;
    padding-top: 5px;
    padding-bottom: 5px;
    color: #535353;
  }

  .open-content-down {
    margin-top: 5%;

    .ant-tabs-tab-active::before {
      position: absolute;
      top: 0px;
      left: 0;
      width: 100%;
      border-top: 2px solid transparent;
      border-radius: 2px 2px 0 0;
      transition: all 0.3s;
      content: '';
      pointer-events: none;
      background-color: rgb(1, 143, 251);
    }
  }
}

.search input {
  width: 100%;
  border: none !important;
}
</style>
<style scoped lang="less">
.tables :deep(td),
.tables :deep(th) {
  font-size: 14px !important;
  padding: 0 8px !important;
}

.tables :deep(th){
  text-align: center !important;
  font-weight: bold;
  background-color: #f2f2f2 !important;
  border-color: #e5e7eb !important;
}

.tables th{
  text-align: center !important;
  font-weight: bold;
  background-color: #f2f2f2 !important;
  border-color: #e5e7eb !important;
}

</style>
