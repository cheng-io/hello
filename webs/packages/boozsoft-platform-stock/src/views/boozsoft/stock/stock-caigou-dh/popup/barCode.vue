<template>
  <BasicModal
    width="500px"
    v-bind="$attrs"
    :closable="false"
    @register="register"
    @ok="handleOk()"
    @cancel="handleClose()"
  >
    <template #title>
      <div style="height: 30px;width: 100%;background-color: #5f375c;color: white;line-height: 30px;text-align: left;">
        <AppstoreOutlined  style="margin: 0 2px;font-size: 14px;"/>
        <span style="font-size: 14px"> 条码录入框</span>
      </div>
    </template>
    <div style="width: 100%;padding: 5%;">
    <div  style="margin-top: 5%;"><span style="font-weight: bold;color: #666666;">条码方案：</span>
      <Select v-model:value="barCodeType" style="width: 150px;">
        <SelectOption value="1">条形码</SelectOption>
        <SelectOption value="2">存货编码</SelectOption>
        <SelectOption value="3">GS1码</SelectOption>
        <SelectOption value="4">MA码</SelectOption>
      </Select>
    </div>
      <div style="margin-top: 5%;">
        <Input v-model:value.trim="barCodeVal" placeholder="条形码扫描录入框" @pressEnter="enterCheck"/>
      </div>
    </div>
  </BasicModal>
</template>
<script setup="props, { content }" lang="ts">
import {AppstoreOutlined} from '@ant-design/icons-vue'
import {ref} from 'vue';
import {BasicModal, useModalInner} from '/@/components/Modal';
import {
  Input ,
  Select,
   message
} from 'ant-design-vue';
import {useMessage} from "/@/hooks/web/useMessage";
import {hasBlank} from "/@/api/task-api/tast-bus-api";
import {useRouteApi} from "/@/utils/boozsoft/datasource/datasourceUtil";
import {findCunHuoAllList} from "/@/api/record/stock/stock-caigou";
import {useCompanyOperateStoreWidthOut} from "/@/store/modules/operate-company";
const { createConfirm } = useMessage()
const SelectOption = Select.Option;
const TextArea = Input.TextArea;
const emit=defineEmits(['register','modify']);
const barCodeType:any = ref('1')
const barCodeVal:any = ref('')
const stockList:any = ref([])
const stockData:any = ref('')
const [register, { closeModal,setModalProps }] = useModalInner( async (data) => {
  barCodeVal.value=''
  findAllStock(data.dynamicTenantId)
  setModalProps({minHeight: 100});
});
function handleClose() {
  return true;
}
async function handleOk() {
  if(stockData.value!==''){return }
  emit('throwData', {data:stockData.value,barCodeType:barCodeType.value})
  closeModal()
  return true;
}
const findAllStock = async (dynamicTenantId) => {
  stockList.value = (await useRouteApi(findCunHuoAllList, {schemaName: dynamicTenantId})({date: useCompanyOperateStoreWidthOut().getLoginDate}))
}
const enterCheck = async () => {
  stockData.value=stockList.value.filter(a=>barCodeType.value=='1'?a.stockBarcode==barCodeVal.value:a.stockNum==barCodeVal.value)[0] || ''
  emit('throwData', {barCodeVal:stockData.value,barCodeType:barCodeType.value})
  closeModal()
}
</script>
<style lang="less" scoped>
:deep(.ant-checkbox){
  margin-top: -8px;
}

:deep(.ant-select-selector) {
  border: none !important;
  border-bottom: 1px solid #bdb9b9 !important;
  background: none;
  width: 80%;
  text-align-last: center;
  .ant-select-selection-item{
    font-weight: bold;color: black;
  }
}

:deep(.ant-input){
  border: 1px solid;
}

:deep(.ant-input)::placeholder{
  font-weight: bold;
  font-size: 20px;
}

.nc-open-content {
  background-image: url(/@/assets/images/homes/bg-pattern.png);
  background-repeat: no-repeat;
  background-position: 66% 8%;
  background-size: contain;
  position: relative;
}

:global(.ant-modal-header) {
  padding: 10px 0 !important;
}

:global(.ant-modal-close-x){
  height: 30px !important;
  color: white;
}

.a-table-font-size-12 :deep(td),
.a-table-font-size-12 :deep(th) {
  font-size: 13px !important;
  padding: 2px 8px !important;
  border-color: #aaaaaa !important;
  font-weight: 600;
}
</style>
