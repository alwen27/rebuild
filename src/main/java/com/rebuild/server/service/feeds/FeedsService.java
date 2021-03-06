/*
Copyright (c) REBUILD <https://getrebuild.com/> and its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.server.service.feeds;

import cn.devezhao.persist4j.PersistManagerFactory;
import com.rebuild.server.metadata.EntityHelper;

import java.util.List;
import java.util.Observer;

/**
 * 动态
 *
 * @author devezhao
 * @since 2019/11/4
 */
public class FeedsService extends BaseFeedsService {

    protected FeedsService(PersistManagerFactory aPMFactory, List<Observer> observers) {
        super(aPMFactory, observers);
    }

    @Override
    public int getEntityCode() {
        return EntityHelper.Feeds;
    }
}
