<template>
  <BasicModal
    width="650px"
    v-bind="$attrs"
    :title="pageMark?'凭证审核':'凭证弃审'"
    cancel-text="确定"
    :showOkBtn="false"
    @register="register">
    <div class="nc-open-content">
      <ul>
        <li>
          <span>本次选择{{ pageMark ? '审核' : '取消审核' }}凭证的数量：</span>
          <a-input v-model:value="resultModel.selectNumber" placeholder=""
                   style="width: 100px;margin-left: 10%;text-align: center;pointer-events: none"/>
          <span>张</span>
        </li>
<!--        <li>
          <span>已经{{ pageMark ? '审核' : '取消审核' }}过的凭证数量：&emsp;</span>
          <a-input placeholder="" v-model:value="resultModel.passNumber"
                   style="width: 100px;margin-left: 10%;text-align: center;pointer-events: none"/>
          <span>张</span>
        </li>-->
        <li>
          <span>本次{{ pageMark ? '审核' : '取消审核' }}成功的凭证数量：</span>
          <a-input placeholder="" v-model:value="resultModel.successNumber"
                   style="width: 100px;margin-left: 10%;text-align: center;pointer-events: none"/>
          <span>张</span>
        </li>
        <li>
          <span>本次{{ pageMark ? '审核' : '取消审核' }}失败的凭证数量：</span>
          <a-input placeholder="" v-model:value="resultModel.errorNumber"
                   style="width: 100px;margin-left: 10%;text-align: center;pointer-events: none"/>
          <span>张</span>
        </li>
      </ul>

      <BasicTable
        :data-source="resultModel.errorList"
        :showIndexColumn="false"
        :pagination="false"
        :scroll="{y: 400 }"
        :columns="[
          {title: '凭证日期',dataIndex: 'dbillDate',width: '120px'},
          {title: '凭证编号',dataIndex: 'inoId',width: '120px',slots: {customRender: 'inoId'}},
          {title: '审核错误原因',dataIndex: 'errorType',width: '300px',slots: {customRender: 'errorType'}},
        ]"
        size="small"
      >
        <template #inoId="{record,index }">
          <span>{{
              voucherNoAutocomplete(record.inoId, voucherRuleSize)
            }}</span>
        </template>
        <template #errorType="{record,index }">
          <span>{{
              voucherErrorTypeAutocomplete(record.errorType, errorTypes)
            }}</span>
        </template>
      </BasicTable>
    </div>
  </BasicModal>
</template>
<script setup="props, {content}" lang="ts">
import {ref} from 'vue'
import {BasicModal, useModalInner} from '/@/components/Modal';
import {BasicTable} from '/@/components/Table';
import {useMessage} from "/@/hooks/web/useMessage";
import {
  voucherNoAutocomplete,
} from "/@/views/boozsoft/system/accvoucher/data";

const {
  createWarningModal,
} = useMessage()
import {
  DatePicker as ADatePicker,
  Select as ASelect,
  Input as AInput,
} from "ant-design-vue";

const ARangePicker = ADatePicker.RangePicker
const ASelectOption = ASelect.Option
const AInputSearch = AInput.Search
// 页面变量
const pageMark = ref(true)
const voucherRuleSize = ref(4)
const errorTypes = {
  '1': `审核与取消审核需为同一人`,
  '2': '作废凭证不需要进行审核',
  '3': '审核日期不能小于凭证日期',
  '4': '标错凭证必须修改为正常凭证后才能进行审核',
  '5': '暂存凭证必须修改为正常凭证后才能进行审核',
  '6': '出纳凭证未进行出纳签字，签字后才能进行审核',
  '7': '凭证借贷方金额不平衡',
  '8': '会计科目非末级',
  '9': '辅助核算项未录入',
  '10': '已审核凭证不可重复审核',
  '11': '已取消审核凭证不可重复取消审核',
  '12': '制单人与审核人不能为同一人',
}
const resultModel = ref({
  selectNumber: 0,
  passNumber: 0,
  successNumber: 0,
  errorNumber: 0,
  errorList: [],
})

const emit = defineEmits(['reload', 'register'])
const [register, {closeModal, changeOkLoading, setModalProps}] = useModalInner((data) => {
  pageMark.value = data.data.mark
  resultModel.value = data.data.result
  voucherRuleSize.value = data.data.voucherRuleSize
  setModalProps({minHeight: 400});
})

const voucherErrorTypeAutocomplete = (key, map) => {
  return map[key] || '其他错误'
}
</script>
<style lang="less" scoped>
:deep(.ant-input) {
  border: none;
  border-bottom: solid 1px rgb(191, 191, 191) !important;
  width: 100%;
}

.nc-open-content {
  margin: 2% 10%;
  color: black;
  font-size: 14px;
  font-weight: bold;
  text-align: center;

  ul {
    margin: 0 5% 5%;

    li {
      margin: 2% 0;

      span:nth-of-type(1) {
        color: slategrey;
      }
    }
  }
}
</style>
