package org.apache.openejb.cdi;

import org.apache.webbeans.component.OwbBean;
import org.apache.webbeans.container.BeanManagerImpl;
import org.apache.webbeans.el.ELContextStore;
import org.apache.webbeans.el22.WebBeansELResolver;

import java.lang.reflect.Type;
import jakarta.el.ELContext;
import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;

public class CustomWebBeansELResolver extends WebBeansELResolver {

    @Override
    protected Object getDependentContextualInstance(BeanManagerImpl manager, ELContextStore store, ELContext context, Bean<?> bean)
    {
        Object contextualInstance = store.getDependent(bean);
        if(contextualInstance != null)
        {
            //Object found on the store
            context.setPropertyResolved(true);
        }
        else
        {
            // If no contextualInstance found on the store
            CreationalContext<?> creationalContext = manager.createCreationalContext(bean);
            contextualInstance = manager.getReference(bean, bestType(bean), creationalContext);
            if (contextualInstance != null)
            {
                context.setPropertyResolved(true);
                //Adding into store
                
                if (! "cc".equals(bean.getName())) {
                    store.addDependent(bean, contextualInstance, creationalContext);
                }                
            }
        }
        return contextualInstance;
    }

    private static Type bestType(Bean<?> bean)
    {
        if (bean == null)
        {
            return Object.class;
        }
        Class<?> bc = bean.getBeanClass();
        if (bc != null)
        {
            return bc;
        }
        if (OwbBean.class.isInstance(bean))
        {
            return OwbBean.class.cast(bean).getReturnType();
        }
        return Object.class;
    }
}
