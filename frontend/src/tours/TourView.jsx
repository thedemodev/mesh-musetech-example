import React, { useState, useEffect, useContext } from 'react';
import { Link } from 'react-router-dom';
import { getTour } from '../api';
import Navigation from '../components/Navigation';
import Footer from '../components/Footer';
import useWebsocketBridge from '../eventbus';
import { Col, Row, Container } from 'react-bootstrap';
import LanguageContext from '../languageContext';
import config from '../config.json';

const trans = {
    de: {
        seats: "Plätze",
        price: "Preis"
    },
    en: {
        seats: "Seats",
        price: "Price"
    }
}
export default function TourView({ match }) {
    // Create state for the component
    const [tour, setTour] = useState();
    const id = match.params.id;
    let lang = useContext(LanguageContext);

    // Register event callback to update the state when content gets changed in Gentics Mesh
    useWebsocketBridge(() => {
        getTour(id, lang).then(setTour);
    });

    // Use effect hook to set the content when the path changes
    useEffect(() => {
        getTour(id, lang).then(setTour);
    }, [id, lang]);

    if (!tour) {
        return null;
    }


    let i18n = trans[lang];

    return (
        <>
            <Navigation />
            <section className="page-section without-header">
                <Container>
                    <div className="content tour-detail-caption bg-light" >
                        <Row>
                            <Col lg={12} className="text-center">
                                <div className="tour-title">
                                    <h2 className="section-heading"><Link to={`/${lang}/tours`}>&lt;&lt;</Link>&nbsp;{tour.fields.title}</h2>
                                </div>
                            </Col>
                        </Row>
                        <Row>
                            <Col lg={12}>
                                <picture>
                                    <source media="(min-height: 320px)" srcSet={`${config.meshUrl}/musetech/webroot${tour.fields.image.path}?w=500&mode=smart`}></source>
                                    <source media="(min-height: 786px)" srcSet={`${config.meshUrl}/musetech/webroot${tour.fields.image.path}?w=800&mode=smart`}></source>
                                    <source media="(min-height: 1280px)" srcSet={`${config.meshUrl}/musetech/webroot${tour.fields.image.path}?w=1200&mode=smart`}></source>
                                    <img alt={tour.fields.name} srcSet={`${config.meshUrl}/musetech/webroot${tour.fields.image.path}?w=600&mode=smart`} className="img-responsive img-fluid" />
                                </picture>
                                <div className="image-attribution">
                                    <p>{tour.fields.image.fields.attribution}</p>
                                </div>
                            </Col>
                        </Row>
                        <Row>
                            <Col lg={12} className="tour-location text-muted">
                                <div>
                                    <b>{i18n.seats}:</b> {tour.fields.size} &nbsp;&nbsp;                                
                                    <b>{i18n.price}:</b> {tour.fields.price}
                                </div>
                            </Col>
                        </Row>
                        <Row> 
                            <Col lg={{ span: 6, offset: 3 }} className="text-left text-muted">
                                <b>Description</b>
                            </Col>
                        </Row>
                        <Row> 
                            <Col lg={{ span: 6, offset: 3 }} className="text-left">
                                <b>{tour.fields.description}</b>
                            </Col>
                        </Row>
                        <Row>
                            <Col lg={{ span: 8, offset: 2 }} className="text-center">
                                <div className="tour-detail-caption">
                                    <p className="text-muted">{tour.fields.description}</p>
                                </div>
                            </Col>
                        </Row>
                    </div>
                </Container>
            </section>
            <Footer />
        </>
    );
}


function Player({ data }) {
    if (data == null || data.path === null) {
        return "";
    }

    return (
        <>
            <audio controls key={data.path}>
                <source src={`${config.meshUrl}/musetech/webroot${data.path}`} type="audio/mp3" />
                Your browser does not support the audio element.
            </audio>
        </>
    );
}