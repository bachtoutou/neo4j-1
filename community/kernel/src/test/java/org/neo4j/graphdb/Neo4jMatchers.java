/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphdb;

import java.util.Collection;
import java.util.Set;

import org.hamcrest.Description;
import org.hamcrest.DiagnosingMatcher;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.neo4j.graphdb.schema.ConstraintDefinition;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.helpers.Function;
import org.neo4j.tooling.GlobalGraphOperations;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.neo4j.helpers.collection.Iterables.map;
import static org.neo4j.helpers.collection.IteratorUtil.asCollection;
import static org.neo4j.helpers.collection.IteratorUtil.asSet;
import static org.neo4j.helpers.collection.IteratorUtil.emptySetOf;

public class Neo4jMatchers
{
    public static <T> Matcher<? super T> inTx( final GraphDatabaseService db, final TypeSafeDiagnosingMatcher<T> inner )
    {
        return new DiagnosingMatcher<T>()
        {
            @Override
            protected boolean matches( Object item, Description mismatchDescription )
            {
                Transaction tx = db.beginTx();
                try
                {
                    if ( inner.matches( item ) )
                    {
                        return true;
                    }

                    inner.describeMismatch( item, mismatchDescription );

                    return false;

                }
                finally
                {
                    tx.finish();
                }
            }

            @Override
            public void describeTo( Description description )
            {
                inner.describeTo( description );
            }
        };
    }

    public static TypeSafeDiagnosingMatcher<Node> hasLabel( final Label myLabel )
    {
        return new TypeSafeDiagnosingMatcher<Node>()
        {
            @Override
            public void describeTo( Description description )
            {
                description.appendValue( myLabel );
            }

            @Override
            protected boolean matchesSafely( Node item, Description mismatchDescription )
            {
                boolean result = item.hasLabel( myLabel );
                if ( !result )
                {
                    Set<String> labels = asLabelNameSet( item.getLabels() );
                    mismatchDescription.appendText( labels.toString() );
                }
                return result;
            }
        };
    }

    public static TypeSafeDiagnosingMatcher<Node> hasLabels( String... expectedLabels )
    {
        return hasLabels( asSet( expectedLabels ) );
    }

    public static TypeSafeDiagnosingMatcher<Node> hasNoLabels()
    {
        return hasLabels( emptySetOf( String.class ) );
    }

    public static TypeSafeDiagnosingMatcher<Node> hasLabels( final Set<String> expectedLabels )
    {
        return new TypeSafeDiagnosingMatcher<Node>()
        {
            private Set<String> foundLabels;

            @Override
            public void describeTo( Description description )
            {
                description.appendText( expectedLabels.toString() );
            }

            @Override
            protected boolean matchesSafely( Node item, Description mismatchDescription )
            {
                foundLabels = asLabelNameSet( item.getLabels() );
                if ( !expectedLabels.containsAll( foundLabels ) )
                {
                    mismatchDescription.appendText( "was " + foundLabels.toString() );
                    return false;
                }

                return true;

            }
        };
    }

    public static TypeSafeDiagnosingMatcher<GlobalGraphOperations> hasNoNodes( final Label withLabel )
    {
        return new TypeSafeDiagnosingMatcher<GlobalGraphOperations>()
        {
            @Override
            protected boolean matchesSafely( GlobalGraphOperations glops, Description mismatchDescription )
            {
                Set<Node> found = asSet( glops.getAllNodesWithLabel( withLabel ) );
                if ( !found.isEmpty() )
                {
                    mismatchDescription.appendText( "found " + found.toString() );
                    return false;
                }
                return true;
            }

            @Override
            public void describeTo( Description description )
            {
                description.appendText( "no nodes with label " + withLabel );
            }
        };
    }

    public static TypeSafeDiagnosingMatcher<GlobalGraphOperations> hasNodes( final Label withLabel, final Node... expectedNodes )
    {
        return new TypeSafeDiagnosingMatcher<GlobalGraphOperations>()
        {
            @Override
            protected boolean matchesSafely( GlobalGraphOperations glops, Description mismatchDescription )
            {
                Set<Node> expected = asSet( expectedNodes );
                Set<Node> found = asSet( glops.getAllNodesWithLabel( withLabel ) );
                if ( !expected.equals( found ) )
                {
                    mismatchDescription.appendText( "found " + found.toString() );
                    return false;
                }
                return true;
            }

            @Override
            public void describeTo( Description description )
            {
                description.appendText( asSet( expectedNodes ).toString() + " with label " + withLabel );
            }
        };
    }

    public static Set<String> asLabelNameSet( Iterable<Label> enums )
    {
        return asSet( map( new Function<Label, String>()
        {
            @Override
            public String apply( Label from )
            {
                return from.name();
            }
        }, enums ) );
    }

    public static class PropertyValueMatcher extends TypeSafeDiagnosingMatcher<Node>
    {
        private final PropertyMatcher propertyMatcher;
        private final String propertyName;
        private final Object expectedValue;

        private PropertyValueMatcher( PropertyMatcher propertyMatcher, String propertyName, Object expectedValue )
        {
            this.propertyMatcher = propertyMatcher;
            this.propertyName = propertyName;
            this.expectedValue = expectedValue;
        }

        @Override
        protected boolean matchesSafely( Node node, Description mismatchDescription )
        {
            if ( !propertyMatcher.matchesSafely( node, mismatchDescription ) )
            {
                return false;
            }

            Object foundValue = node.getProperty( propertyName );
            if ( !foundValue.equals( expectedValue ) )
            {
                mismatchDescription.appendText( "found value " + formatValue( foundValue ) );
                return false;
            }
            return true;
        }

        @Override
        public void describeTo( Description description )
        {
            propertyMatcher.describeTo( description );
            description.appendText( String.format( "having value %s", formatValue( expectedValue ) ) );
        }

        private String formatValue(Object v)
        {
            if (v instanceof String)
            {
                return String.format("'%s'", v.toString());
            }
            return v.toString();
        }

    }

    public static class PropertyMatcher extends TypeSafeDiagnosingMatcher<Node>
    {

        public final String propertyName;

        private PropertyMatcher( String propertyName )
        {
            this.propertyName = propertyName;
        }

        @Override
        protected boolean matchesSafely( Node node, Description mismatchDescription )
        {
            if ( !node.hasProperty( propertyName ) )
            {
                mismatchDescription.appendText( String.format( "found node without property named '%s'",
                        propertyName ) );
                return false;
            }
            return true;
        }

        @Override
        public void describeTo( Description description )
        {
            description.appendText( String.format( "node with property name '%s' ", propertyName ) );
        }

        public PropertyValueMatcher withValue( Object value )
        {
            return new PropertyValueMatcher( this, propertyName, value );
        }
    }

    public static PropertyMatcher hasProperty( String propertyName )
    {
        return new PropertyMatcher( propertyName );
    }


    public static Deferred<Node> findNodesByLabelAndProperty( final Label label, final String propertyName,
                                                              final Object propertyValue,
                                                              final GraphDatabaseService db )
    {
        return new Deferred<Node>(db)
        {
            @Override
            protected Iterable<Node> manifest()
            {
                return db.findNodesByLabelAndProperty( label, propertyName, propertyValue );
            }
        };
    }

    public static Deferred<IndexDefinition> getIndexes( final GraphDatabaseService db, final Label label )
    {
        return new Deferred<IndexDefinition>( db )
        {
            @Override
            protected Iterable<IndexDefinition> manifest()
            {
                return db.schema().getIndexes( label );
            }
        };
    }

    public static Deferred<String> getPropertyKeys( final GraphDatabaseService db,
                                                    final PropertyContainer propertyContainer )
    {
        return new Deferred<String>( db )
        {
            @Override
            protected Iterable<String> manifest()
            {
                return propertyContainer.getPropertyKeys();
            }
        };
    }

    public static Deferred<ConstraintDefinition> getConstraints( final GraphDatabaseService db, final Label label )
    {
        return new Deferred<ConstraintDefinition>( db )
        {
            @Override
            protected Iterable<ConstraintDefinition> manifest()
            {
                return db.schema().getConstraints( label );
            }
        };
    }

    public static Deferred<ConstraintDefinition> getConstraints( final GraphDatabaseService db )
    {
        return new Deferred<ConstraintDefinition>( db )
        {
            @Override
            protected Iterable<ConstraintDefinition> manifest()
            {
                return db.schema().getConstraints( );
            }
        };
    }

    /**
     * Represents test data that can at assertion time produce a collection
     *
     * Useful to defer actually doing operations until context has been prepared (such as a transaction created)
     *
     * @param <T> The type of objects the collection will contain
     */
    public static abstract class Deferred<T>
    {

        private final GraphDatabaseService db;

        public Deferred( GraphDatabaseService db )
        {
            this.db = db;
        }

        protected abstract Iterable<T> manifest();

        public Collection<T> collection()
        {
            Transaction tx = db.beginTx();
            try
            {
                return asCollection( manifest() );
            }
            finally
            {
                tx.finish();
            }
        }

    }

    public static <T> TypeSafeDiagnosingMatcher<Neo4jMatchers.Deferred<T>> containsOnly( final T... expectedObjects )
    {
        return new TypeSafeDiagnosingMatcher<Neo4jMatchers.Deferred<T>>()
        {
            @Override
            protected boolean matchesSafely( Neo4jMatchers.Deferred<T> nodes, Description description )
            {
                Set<T> expected = asSet( expectedObjects );
                Set<T> found = asSet( nodes.collection() );
                if ( !expected.equals( found ) )
                {
                    description.appendText( "found " + found.toString() );
                    return false;
                }
                return true;
            }

            @Override
            public void describeTo( Description description )
            {
                description.appendText( "exactly " + asSet( expectedObjects ) );
            }
        };
    }

    public static <T> TypeSafeDiagnosingMatcher<Neo4jMatchers.Deferred<T>> contains( final T... expectedObjects )
    {
        return new TypeSafeDiagnosingMatcher<Neo4jMatchers.Deferred<T>>()
        {
            @Override
            protected boolean matchesSafely( Neo4jMatchers.Deferred<T> nodes, Description description )
            {
                Set<T> expected = asSet( expectedObjects );
                Set<T> found = asSet( nodes.collection() );
                if ( !found.containsAll( expected ) )
                {
                    description.appendText( "found " + found.toString() );
                    return false;
                }
                return true;
            }

            @Override
            public void describeTo( Description description )
            {
                description.appendText( "contains " + asSet( expectedObjects ) );
            }
        };
    }

    public static TypeSafeDiagnosingMatcher<Neo4jMatchers.Deferred<?>> isEmpty( )
    {
        return new TypeSafeDiagnosingMatcher<Deferred<?>>()
        {
            @Override
            protected boolean matchesSafely( Deferred<?> deferred, Description description )
            {
                Collection<?> collection = deferred.collection();
                if(!collection.isEmpty())
                {
                    description.appendText( "was " + collection.toString() );
                    return false;
                }

                return true;
            }

            @Override
            public void describeTo( Description description )
            {
                description.appendText( "empty collection" );
            }
        };
    }

    public static IndexDefinition createIndex( GraphDatabaseService beansAPI, Label label, String property )
    {
        Transaction tx = beansAPI.beginTx();
        IndexDefinition indexDef;
        try
        {
            indexDef = beansAPI.schema().indexFor( label ).on( property ).create();
            tx.success();
        }
        finally
        {
            tx.finish();
        }

        waitForIndex( beansAPI, indexDef );
        return indexDef;
    }

    public static void waitForIndex( GraphDatabaseService beansAPI, IndexDefinition indexDef )
    {
        Transaction tx;
        tx = beansAPI.beginTx();
        try
        {
            beansAPI.schema().awaitIndexOnline( indexDef, 10, SECONDS );
        }
        finally
        {
            tx.finish();
        }
    }
}
