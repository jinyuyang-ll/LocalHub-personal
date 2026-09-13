package com.hmdp.controller.api;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/shops")
public class ApiShopController {

    @Resource
    private IShopService shopService;

    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    @GetMapping("/search")
    public Result search(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return shopService.searchShops(name, current);
    }

    @GetMapping("/nearby")
    public Result nearby(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("x") Double x,
            @RequestParam("y") Double y
    ) {
        return shopService.queryShopByType(typeId, current, x, y);
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable("id") Long id, @RequestBody Shop shop) {
        shop.setId(id);
        return shopService.update(shop);
    }
}
