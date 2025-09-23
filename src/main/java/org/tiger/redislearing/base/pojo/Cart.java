package org.tiger.redislearing.base.pojo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @ClassName Cart
 * @Description 购物车实体类
 * @Author tiger
 * @Date 2025/9/22 10:50
 */
@Data
@Accessors(chain = true)
public class Cart {
    private String productId;
    private String productName;
    private String productPrice;
}
