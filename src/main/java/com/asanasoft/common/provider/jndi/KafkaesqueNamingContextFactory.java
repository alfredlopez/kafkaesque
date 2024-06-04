package com.asanasoft.common.provider.jndi;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.spi.InitialContextFactory;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;

public class KafkaesqueNamingContextFactory  implements InitialContextFactory, ObjectFactory {
    @Override
    public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
        return new KafkaesqueNamingContext();
    }

    @Override
    public Object getObjectInstance(Object obj, Name name, Context nameCtx, Hashtable<?, ?> environment) throws Exception {
        Context ctx = this.getInitialContext(environment);
        Reference ref = (Reference)obj;
        return ctx.lookup((String)ref.get("URL").getContent());
    }
}
