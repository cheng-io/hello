import { defineComponent, computed, unref } from 'vue';
import { Tooltip } from 'ant-design-vue';
import { InfoCircleOutlined } from '@ant-design/icons-vue';
import { getPopupContainer } from '/@/utils';
import { isString, isArray } from '/@/utils/is';
import { getSlot } from '/@/utils/helper/tsxHelper';
import { propTypes } from '/@/utils/propTypes';
import { useDesign } from '/@/hooks/web/useDesign';
export default defineComponent({
    name: 'BasicHelp',
    components: { Tooltip },
    props: {
        // max-width
        maxWidth: propTypes.string.def('600px'),
        // Whether to display the serial number
        showIndex: propTypes.bool,
        // color
        color: propTypes.string.def('#ffffff'),
        fontSize: propTypes.string.def('14px'),
        placement: propTypes.string.def('right'),
        absolute: propTypes.bool,
        // Text list
        text: {
            type: [Array, String],
        },
        // 定位
        position: {
            type: [Object],
            default: () => ({
                position: 'absolute',
                left: 0,
                bottom: 0,
            }),
        },
    },
    setup(props, { slots }) {
        const { prefixCls } = useDesign('basic-help');
        const getOverlayStyle = computed(() => {
            return {
                maxWidth: props.maxWidth,
            };
        });
        const getWrapStyle = computed(() => {
            return {
                color: props.color,
                fontSize: props.fontSize,
            };
        });
        const getMainStyleRef = computed(() => {
            return props.absolute ? props.position : {};
        });
        const renderTitle = () => {
            const list = props.text;
            if (isString(list)) {
                return <p>{list}</p>;
            }
            if (isArray(list)) {
                return list.map((item, index) => {
                    return (<p key={item}>
                <>
                  {props.showIndex ? `${index + 1}. ` : ''}
                  {item}
                </>
              </p>);
                });
            }
            return null;
        };
        return () => {
            return (<Tooltip title={<div style={unref(getWrapStyle)}>{renderTitle()}</div>} overlayClassName={`${prefixCls}__wrap`} autoAdjustOverflow={true} overlayStyle={unref(getOverlayStyle)} placement={props.placement} getPopupContainer={() => getPopupContainer()}>
            <span class={prefixCls} style={unref(getMainStyleRef)}>
              {getSlot(slots) || <InfoCircleOutlined />}
            </span>
          </Tooltip>);
        };
    },
});
//# sourceMappingURL=BasicHelp.vue.jsx.map
