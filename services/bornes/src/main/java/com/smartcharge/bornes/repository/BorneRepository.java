
package com.smartcharge.bornes.repository;

import com.smartcharge.bornes.model.Borne;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BorneRepository extends MongoRepository<Borne, String> {

}
