package org.apache.openejb.arquillian.tests.cmp.sample;

import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Singleton
@Lock(LockType.READ)
public class UserBean {

    @PersistenceContext(name = "user-pu")
    private EntityManager em;

    public void addUser(final Long id, final String firstname, final String lastname) {
        final User user = new User();
        user.setId(id);
        user.setFirstname(firstname);
        user.setLastname(lastname);

        em.persist(user);
    }

}
