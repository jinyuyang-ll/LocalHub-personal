package com.hmdp.controller.api;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/shops")
@Validated
public class ApiShopController {

    @Resource
    private IShopService shopService;

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") @Positive Long id) {
        return shopService.queryById(id);
    }

    @GetMapping("/search")
    public Result search(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") @Positive Integer current
    ) {
        return shopService.searchShops(name, current);
    }

    @GetMapping("/nearby")
    public Result nearby(
            @RequestParam("typeId") @Positive Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") @Positive Integer current,
            @RequestParam("x") Double x,
            @RequestParam("y") Double y
    ) {
        return shopService.queryShopByType(typeId, current, x, y);
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable("id") @Positive Long id, @RequestBody Shop shop) {
        shop.setId(id);
        return shopService.update(shop);
    }
}
