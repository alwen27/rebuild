/*
rebuild - Building your business-systems freely.
Copyright (C) 2019 devezhao <zhaofang123@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rebuild.server.service.configuration;

import com.rebuild.server.Application;
import com.rebuild.server.configuration.portals.AdvFilterManager;
import com.rebuild.server.metadata.EntityHelper;
import com.rebuild.server.service.DataSpecificationException;
import com.rebuild.server.service.bizz.UserHelper;

import cn.devezhao.persist4j.PersistManagerFactory;
import cn.devezhao.persist4j.engine.ID;

/**
 * @author devezhao-mbp zhaofang123@gmail.com
 * @since 2019/04/30
 */
public class AdvFilterService extends ConfigurationService {
	
	protected AdvFilterService(PersistManagerFactory aPMFactory) {
		super(aPMFactory);
	}
	
	@Override
	public int getEntityCode() {
		return EntityHelper.FilterConfig;
	}
	
	@Override
	public int delete(ID filterId) {
		ID user = Application.getCurrentUser();
		if (!(UserHelper.isAdmin(user) || AdvFilterManager.instance.isSelf(user, filterId))) {
			throw new DataSpecificationException("无权删除");
		}
		return super.delete(filterId);
	}

	@Override
	protected void cleanCache(ID configId) {
		AdvFilterManager.instance.clean(configId);
	}
}