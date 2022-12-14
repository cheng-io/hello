package org.boozsoft.util;

import java.math.BigDecimal;

/**
 * @ClassName : BigDecimalUtils
 * @Description : 求和-必须是lambda方式
 * @Author : miao
 * @Date: 2021-05-26 11:01
 */
public class BigDecimalUtils {
    public static BigDecimal ifNullSet0(BigDecimal in) {
        if (in != null) {
            return in;
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal sum(BigDecimal ...in){

        BigDecimal result = BigDecimal.ZERO;
        for (int i = 0; i < in.length; i++){
            result = result.add(ifNullSet0(in[i]));
        }
        return result;
    }
}
